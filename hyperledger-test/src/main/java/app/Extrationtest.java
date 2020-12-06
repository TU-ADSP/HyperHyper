package app;

import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;

public class Extrationtest {
	public static void main(String[] args) {
        // Configure the gateway connection used to access the network.
        Gateway.Builder builder = Gateway.createBuilder();

        Gateway gateway = builder.connect();
        // Obtain a smart contract deployed on the network.
        Network network = gateway.getNetwork("mychannel");
        // Contract contract = network.getContract("fabcar");

        System.out.println(network.toString());
        // System.out.println(contract.toString());
	}			
}
