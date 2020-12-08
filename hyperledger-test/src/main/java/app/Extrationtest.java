package app;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.function.Consumer;

import org.apache.commons.codec.binary.Base64;
import org.hyperledger.fabric.gateway.*;
import org.hyperledger.fabric.sdk.BlockEvent;

public class Extrationtest {
	public static void main(String[] args) throws Exception {
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
			Contract contract = network.getContract("basic");

			Consumer<BlockEvent> consumer = (BlockEvent e) -> {
				if (e != null) {
					System.out.println(e.getBlockNumber());
					for (BlockEvent.TransactionEvent te : e.getTransactionEvents()) {
						System.out.println(te.getTimestamp());
					}
				} else {
					System.out.println("e was null");
				}
			};

			if (args[0].equals("network")) {
				synchronized (network) {
					network.addBlockListener(0, consumer);
					network.wait();
				}
			} else {
				synchronized (contract) {
					contract.addContractListener(0, (ContractEvent ce) -> {
						System.out.println(new String(ce.getPayload().get()));
					});

					contract.wait();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
