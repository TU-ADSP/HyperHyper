package app;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.hyperledger.fabric.gateway.*;
import org.hyperledger.fabric.sdk.BlockEvent;

public class Extrationtest {

	// inspired from: https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
	private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
	public static String bytesToHex(byte[] bytes) {
		byte[] hexChars = new byte[bytes.length * 2];
		for (int j = 0; j < bytes.length; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return new String(hexChars, StandardCharsets.UTF_8);
	}

	public static void main(String[] args) throws Exception {
		int startBlock = 0;
		int endBlock = Integer.MAX_VALUE;
		if (args.length < 1) {
			System.out.println("Please provide mode as first argument: 'network', 'contract' or 'combined'");
			System.exit(1);
		}
		if (args.length > 1) {
			startBlock = Integer.parseInt(args[1]);
		}
		if (args.length > 2) {
			endBlock = Integer.parseInt(args[2]);
		}
		if (startBlock >= endBlock) {
			System.out.println("Please provide a start block number that is smaller than the end block number!");
			System.exit(1);
		}
		final int endBlockLambda = endBlock;

	    // Channel connection preparation
		// Path to a common connection profile describing the network.
		Path networkConfigFile = Paths.get("src/main/resources/connection-org1.yaml");

		// certificate
		InputStream inStream = new FileInputStream("src/main/resources/server.crt");
		X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X509")
				.generateCertificate(inStream);
		inStream.close();

		// key
		String key = new String(Files.readAllBytes(Paths.get("src/main/resources/server.key")),
				Charset.defaultCharset());

		String privateKeyPEM = key.replace("-----BEGIN PRIVATE KEY-----", "").replaceAll(System.lineSeparator(), "")
				.replace("-----END PRIVATE KEY-----", "");

		byte[] encoded = Base64.decodeBase64(privateKeyPEM);

		KeyFactory keyFactory = KeyFactory.getInstance("EC");
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
	    PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

		// Configure the gateway connection used to access the network.
		Gateway.Builder builder = Gateway.createBuilder()
				.identity(Identities.newX509Identity("Org1MSP", certificate, privateKey))
				.networkConfig(networkConfigFile);

		// Create a gateway connection
		try (Gateway gateway = builder.connect()) {
			// Obtain a smart contract deployed on the network.
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("kitties");

			if (args[0].equals("network")) {
				network.addBlockListener(startBlock, (BlockEvent e) -> {
					if (e.getBlockNumber() <= endBlockLambda) {
						System.out.println(e.getBlockNumber() + ", "  + bytesToHex(e.getDataHash()) + ", " + e.getBlock().getData().getSerializedSize());
					}
					if (e.getBlockNumber() >= endBlockLambda) {
					    synchronized (network) {
							network.notifyAll();
						}
					}
				});
				synchronized (network) {
					network.wait();
				}
			} else {
				contract.addContractListener(startBlock, (ContractEvent ce) -> {
					if (ce.getTransactionEvent().getBlockEvent().getBlockNumber() <= endBlockLambda) {
						System.out.println(ce.getTransactionEvent().getBlockEvent().getBlockNumber() + ", " + ce.getTransactionEvent().getTimestamp() + ", " + new String(ce.getPayload().get()));
					}
					if (ce.getTransactionEvent().getBlockEvent().getBlockNumber() >= endBlockLambda) {
						synchronized (contract) {
							contract.notifyAll();
						}
					}
				});
				synchronized (contract) {
					contract.wait();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
