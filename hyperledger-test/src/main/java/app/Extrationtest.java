package app;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Identities;
import org.hyperledger.fabric.gateway.Network;

public class Extrationtest {
	public static void main(String[] args) throws Exception {
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
			Contract contract = network.getContract("fabcar");

			// Submit transactions that store state to the ledger.
			byte[] createCarResult = contract.createTransaction("createCar").submit("CAR10", "VW", "Polo", "Grey",
					"Mary");
			System.out.println(new String(createCarResult, StandardCharsets.UTF_8));

			// Evaluate transactions that query state from the ledger.
			byte[] queryAllCarsResult = contract.evaluateTransaction("queryAllCars");
			System.out.println(new String(queryAllCarsResult, StandardCharsets.UTF_8));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
