package com.hotak.noonchibot.connector.hyperliquid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hotak.noonchibot.connector.web.Authenticator;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsRequest;
import org.msgpack.jackson.dataformat.MessagePackMapper;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;

public class HyperliquidAuthenticator implements Authenticator {
    private final ObjectMapper objectMapper;
    private final MessagePackMapper messagePackMapper;
    private final String apiAddress;
    private final String vaultAddress;
    // mainnet -> true,  testnet -> false
    private final boolean isMainnet;
    private final ECKeyPair masterKeyPair;
    private final NonceManager nonceManager;
    private final L1PhantomBuilder l1Builder = new L1PhantomBuilder();
    private final UserSignedBuilder userBuilder = new UserSignedBuilder();

    public HyperliquidAuthenticator(
            ObjectMapper objectMapper,
            MessagePackMapper messagePackMapper,
            String vaultAddress,
            boolean isMainnet,
            String apiAddress,
            String masterApiSecret
    ) {
        this.objectMapper = objectMapper;
        this.messagePackMapper = messagePackMapper;
        this.vaultAddress = vaultAddress;
        this.isMainnet = isMainnet;
        this.apiAddress = Objects.requireNonNull(apiAddress);
        this.masterKeyPair =ECKeyPair.create(Numeric.toBigInt(masterApiSecret));
        this.nonceManager = new NonceManager();
    }

    @Override
    public RestRequest restAuthenticate(RestRequest restRequest) {
        Object signedBody = addAuthToBody(restRequest.body());
        return restRequest.toBuilder().body(signedBody).build();
    }

    @Override
    public WsRequest wsAuthenticate(WsRequest wsRequest) {
        return wsRequest; //by pass
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> addAuthToBody(Object body) {
        if (body == null) return Map.of();
        Map<String, Object> request = (Map<String, Object>) body;
        Map<String, Object> action = new LinkedHashMap<>((Map<String, Object>) request.get("action"));

        long nonce = nonceManager.nextMs();
        String type = (String) action.get("type");

        // schema 등록된 action만 user-signed, 나머지는 L1
        Map<String, Object> typedData;

        if (userBuilder.canHandle(type)) {
            typedData = userBuilder.build(action, nonce);
        } else {
            typedData = l1Builder.build(action, nonce);
        }
        Map<String, Object> signature = signTypedData(typedData);
        return buildSignedPayload(action, nonce, signature);
    }

    /**
     * 공통 ECDSA 서명
     */
    private Map<String, Object> signTypedData(Map<String, Object> typedData) {
        try {
            String json = objectMapper.writeValueAsString(typedData);
            StructuredDataEncoder encoder = new StructuredDataEncoder(json);
            byte[] hash = encoder.hashStructuredData();
            Sign.SignatureData sig = Sign.signMessage(hash, masterKeyPair, false);

            Map<String, Object> signature = new LinkedHashMap<>();
            signature.put("r", "0x" + Numeric.toHexStringNoPrefixZeroPadded(
                    new BigInteger(1, sig.getR()), 64));
            signature.put("s", "0x" + Numeric.toHexStringNoPrefixZeroPadded(
                    new BigInteger(1, sig.getS()), 64));
            signature.put("v", sig.getV()[0] & 0xFF);
            return signature;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 공통 payload 구성
     */
    private Map<String, Object> buildSignedPayload(Object action, long nonce, Map<String, Object> signature) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("nonce", nonce);
        payload.put("signature", signature);
        if (vaultAddress != null) {
            payload.put("vaultAddress", vaultAddress);
        }
        return payload;
    }

    private static class NonceManager {
        private long lastNonce;
        private final Object lock = new Object();

        public NonceManager() {
            this.lastNonce = System.currentTimeMillis();
        }

        public long nextMs() {
            synchronized (lock) {
                long now = System.currentTimeMillis();
                if (now <= lastNonce) {
                    now = lastNonce + 1;
                }
                lastNonce = now;
                return now;
            }
        }
    }

    /**
     * L1 phantom agent: msgpack hash → connection id로 wrap.
     */
    private class L1PhantomBuilder {
        public Map<String, Object> build(Map<String, Object> action, long nonce) {
            byte[] hash = actionHash(action, nonce);

            Map<String, Object> phantomAgent = new LinkedHashMap<>();
            phantomAgent.put("source", isMainnet ? "a" : "b");
            phantomAgent.put("connectionId", hash);

            Map<String, Object> typedData = new LinkedHashMap<>();

            Map<String, Object> domain = new LinkedHashMap<>();
            domain.put("chainId", 1337);
            domain.put("name", "Exchange");
            domain.put("verifyingContract", "0x0000000000000000000000000000000000000000");
            domain.put("version", "1");
            typedData.put("domain", domain);

            Map<String, Object> types = new LinkedHashMap<>();
            types.put("EIP712Domain", List.of(
                    Map.of("name", "name", "type", "string"),
                    Map.of("name", "version", "type", "string"),
                    Map.of("name", "chainId", "type", "uint256"),
                    Map.of("name", "verifyingContract", "type", "address")
            ));
            types.put("Agent", List.of(
                    Map.of("name", "source", "type", "string"),
                    Map.of("name", "connectionId", "type", "bytes32")
            ));
            typedData.put("types", types);
            typedData.put("primaryType", "Agent");
            typedData.put("message", phantomAgent);

            return typedData;
        }

        private byte[] actionHash(Object action, long nonce) {
            try {
                byte[] actionBytes = messagePackMapper.writeValueAsBytes(action);
                ByteBuffer buffer = ByteBuffer.allocate(actionBytes.length + 8 + 1 + 20);
                buffer.put(actionBytes);
                buffer.putLong(nonce);

                if (vaultAddress == null) {
                    buffer.put((byte) 0x00);
                } else {
                    buffer.put((byte) 0x01);
                    buffer.put(addressToBytes(vaultAddress));
                }
                byte[] data = new byte[buffer.position()];
                buffer.rewind();
                buffer.get(data);

                return Hash.sha3(data);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        private byte[] addressToBytes(String address) {
            String hex = address.startsWith("0x") ? address.substring(2) : address;
            return Numeric.hexStringToByteArray(hex.toLowerCase());
        }
    }

    /**
     * User-signed EIP-712
     */
    private class UserSignedBuilder {
        private static final List<Map<String, String>> SEND_ASSET_TYPES = List.of(
                Map.of("name", "hyperliquidChain", "type", "string"),
                Map.of("name", "destination", "type", "string"),
                Map.of("name", "sourceDex", "type", "string"),
                Map.of("name", "destinationDex", "type", "string"),
                Map.of("name", "token", "type", "string"),
                Map.of("name", "amount", "type", "string"),
                Map.of("name", "fromSubAccount", "type", "string"),
                Map.of("name", "nonce", "type", "uint64")
        );

        // 등록된 action → (primaryType, fields)
        // 새 user-signed action 추가 시 여기 등록만 하면 됨
        private final Map<String, ActionInfo> registeredActions = Map.of(
                "sendAsset", new ActionInfo(
                        "HyperliquidTransaction:SendAsset",
                        SEND_ASSET_TYPES
                )
                // 필요해지면 추가:
                // "usdSend", new ActionInfo("HyperliquidTransaction:UsdSend", USD_SEND_TYPES),
                // "withdraw3", new ActionInfo("HyperliquidTransaction:Withdraw", WITHDRAW_TYPES),
                // "usdClassTransfer", new ActionInfo("HyperliquidTransaction:UsdClassTransfer", USD_CLASS_TRANSFER_TYPES),
        );

        public boolean canHandle(String type) {
            return registeredActions.containsKey(type);
        }

        public Map<String, Object> build(Map<String, Object> action, long nonce) {
            String type = (String) action.get("type");
            ActionInfo info = registeredActions.get(type);
            action.put("hyperliquidChain", isMainnet ? "Mainnet" : "Testnet");
            action.put("signatureChainId", "0x66eee");
            if (action.containsKey("time")) {
                action.put("time", nonce);
            } else {
                action.put("nonce", nonce);
            }

            Map<String, Object> message = new LinkedHashMap<>();
            for (Map<String, String> field : info.fields()) {
                String name = field.get("name");
                Object value = action.get(name);
                if (value == null) {
                    throw new IllegalStateException("Missing required field '" + name + "' for action: " + type);
                }
                message.put(name, value);
            }
            Map<String, Object> typedData = new LinkedHashMap<>();

            Map<String, Object> domain = new LinkedHashMap<>();
            domain.put("name", "HyperliquidSignTransaction");
            domain.put("version", "1");
            domain.put("chainId", 421614L);  // 0x66eee
            domain.put("verifyingContract", "0x0000000000000000000000000000000000000000");
            typedData.put("domain", domain);

            Map<String, Object> types = new LinkedHashMap<>();
            types.put("EIP712Domain", List.of(
                    Map.of("name", "name", "type", "string"),
                    Map.of("name", "version", "type", "string"),
                    Map.of("name", "chainId", "type", "uint256"),
                    Map.of("name", "verifyingContract", "type", "address")
            ));
            types.put(info.primaryType(), info.fields());
            typedData.put("types", types);
            typedData.put("primaryType", info.primaryType());
            typedData.put("message", message);
            return typedData;
        }

        private record ActionInfo(
                String primaryType,
                List<Map<String, String>> fields
        ) {}

    }
}
