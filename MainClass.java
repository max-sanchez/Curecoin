/*
 * Curecoin 2.0.0a Source Code
 * Copyright (c) 2015 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */
import java.io.*;
import java.awt.*;
import java.util.*;

/**
 * Welcome to the Curecoin 2.0.0a1 source code! MainClass stitches all of the separate components together to make everything work.
 * Quick overview of the program's structure:
 * CurecoinDatabaseManager handles all database-related workloads, such as storing/adding blocks, account balance lookups, etc.
 * PendingTransactionContainer is simply a glorified ArrayList<String> to hold pending transactions.
 * PeerNetwork handles all P2P networking, delegating network workloads to PeerThread, which delegates to InputThread and OutputThread.
 * RPC handles all RPC calls. At ths point, RPC holds the most recent RPC call in a String, and MainClass is expected to loop quickly enough
 * to grab RPC calls and plop the appropriate response into a object variable. 
 */
public class MainClass
{
    public static void main(String[] args)
    {
        //launch();
        //Start of the program, initialize Database object
        CurecoinDatabaseMaster databaseMaster = new CurecoinDatabaseMaster("database");
        PendingTransactionContainer pendingTransactionContainer = new PendingTransactionContainer();
        PeerNetwork peerNetwork = new PeerNetwork();
        peerNetwork.start();
        RPC rpcAgent = new RPC();
        rpcAgent.start();
        File peerFile = new File("peers.lst");
        ArrayList<String> peers = new ArrayList<String>();
        AddressManager addressManager = new AddressManager();
        if (!peerFile.exists())
        {
            try
            {
                PrintWriter out = new PrintWriter(peerFile);
                /*
                 * In future networks, these will route to servers running the daemon. For now, it's just the above IP.
                 */
                //out.println("2.curecoinmirror.com:8015");
                //out.println("3.curecoinmirror.com:8015");
                out.close();
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        try
        {
            Scanner scan = new Scanner(peerFile);
            while (scan.hasNextLine())
            {
                String combo = scan.nextLine();
                peers.add(combo);
                String host = combo.substring(0, combo.indexOf(":"));
                int port = Integer.parseInt(combo.substring(combo.indexOf(":") + 1));
                peerNetwork.connectToPeer(host, port);
            }
            Thread.sleep(4000);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        //peerNetwork.connectToPeer("127.0.0.1", 8125);
        peerNetwork.broadcast("REQUEST_NET_STATE");
        int topBlock = 0;
        ArrayList<String> allBroadcastTransactions = new ArrayList<String>();
        ArrayList<String> allBroadcastBlocks = new ArrayList<String>();
        while (true) 
        {
            //Look for new data from peers
            for (int i = 0; i < peerNetwork.peerThreads.size(); i++)
            {
                ArrayList<String> input = peerNetwork.peerThreads.get(i).inputThread.readData();
                if (input == null)
                {
                    System.out.println("NULL RET RETRY");
                    System.exit(-4);
                    break;
                }
                /*
                 * While taking up new transactions and blocks, the client will broadcast them to the network if they are new to the client.
                 * As a result, if you are connected to 7 peers, you will get reverb 7 times for a broadcast of a block or transaction.
                 * For now, this is done to MAKE SURE everyone is on the same page with block/transaction propagation.
                 * In the future, much smarter algorithms for routing, perhaps sending "have you seen xxx transaction" or similar will be used.
                 * No point in sending 4 KB when a 64-byte message (or less) could check to make sure a transaction hasn't already been sent.
                 * Not wanting to complicate 2.0.0a1, there are no fancy algorithms or means of telling if peers have already heard the news you are going to deliver.
                 */
                for (int j = 0; j < input.size(); j++)
                {
                    String data = input.get(j);
                    if (data.length() > 60)
                    {
                        System.out.println("got data: " + data.substring(0, 30) + "..." + data.substring(data.length() - 30, data.length()));
                    } else
                    {
                        System.out.println("got data: " + data);
                    }
                    String[] parts = data.split(" ");
                    if (parts.length > 0)
                    {
                        //NETWORK_STATE MAX_HEIGHT LAST_HASH
                        if (parts[0].equalsIgnoreCase("NETWORK_STATE"))
                        {
                            topBlock = Integer.parseInt(parts[1]);
                        }
                        else if (parts[0].equalsIgnoreCase("REQUEST_NET_STATE"))
                        {
                            peerNetwork.peerThreads.get(i).outputThread.write("NETWORK_STATE " + databaseMaster.getBlockchainLength() + " " + databaseMaster.getLatestBlock().blockHash);
                            for (int k = 0; k < pendingTransactionContainer.pendingTransactions.size(); k++)
                            {
                                peerNetwork.peerThreads.get(i).outputThread.write("TRANSACTION " + pendingTransactionContainer.pendingTransactions.get(k));
                            }
                        }
                        //BLOCK BLOCKDATA
                        else if (parts[0].equalsIgnoreCase("BLOCK"))
                        {
                            /*
                             * If a block is new to the client, the client will attempt to add it to the blockchain.
                             * When added to the blockchain, it may get added to a chain, put on a new fork, put on an existing, shorter-length chain that's forked less than 10 blocks back, or
                             * it may end up being queued or deleted. Queued blocks are blocks that self-validate (signatures match, etc.) but don't fit onto any chain.
                             * They are often used when getting blocks from a peer, in case one arrives out of order.
                             */
                            System.out.println("Attempting to add block...");
                            boolean hasSeenBefore = false;
                            for (int k = 0; k < allBroadcastBlocks.size(); k++)
                            {
                                //Likely due to P2P reverb / echo
                                System.out.println("Have seen block before... not adding.");
                                if (parts[1].equals(allBroadcastBlocks.get(k)))
                                {
                                    hasSeenBefore = true;
                                }
                            }
                            if (!hasSeenBefore)
                            {
                                //Block has not been previously received, so it will be added to the blockchain (hopefully)
                                System.out.println("Adding new block from network!");
                                System.out.println("Block: ");
                                System.out.println(parts[1]);
                                allBroadcastBlocks.add(parts[1]);
                                Block blockToAdd = new Block(parts[1]);
                                if (databaseMaster.addBlock(blockToAdd))
                                {
                                    //If block is new to client and appears valid, rebroadcast
                                    System.out.println("Added block " + blockToAdd.blockNum + " with hash: [" + blockToAdd.blockHash.substring(0, 30) + "..." + blockToAdd.blockHash.substring(blockToAdd.blockHash.length() - 30, blockToAdd.blockHash.length() - 1) + "]");
                                    peerNetwork.broadcast("BLOCK " + parts[1]);
                                }
                                //Remove all transactions from the pendingTransactionPool that appear in the block
                                pendingTransactionContainer.removeTransactionsInBlock(parts[1]);
                            }
                        }
                        //TRANSACTION TRANSACTIONDATA
                        else if (parts[0].equalsIgnoreCase("TRANSACTION"))
                        {
                            /*
                             * Any transactions that are received will be checked against the table of existing received transactions. If they are new (and validate correctly), they will be added
                             * to the pending transaction pool. Currently, this pool is only useful when mining blocks. In the future, this pool will be accessible using RPC commands to show
                             * unconfirmed transactions, etc.
                             */
                            boolean alreadyExisted = false;
                            for (int b = 0; b < allBroadcastTransactions.size(); b++)
                            {
                                if (parts[1].equals(allBroadcastTransactions.get(b)))
                                {
                                    alreadyExisted = true;
                                }
                            }
                            if (!alreadyExisted) //Transaction was not already received
                            {
                                /*
                                 * Put the transaction in the received transactions pile, check it for validity, and put it in the pool if valid.
                                 * Important to note--validity checks are done by PendingTransactionContainer's addTransaction(String transaction) method.
                                 * Also important to note--We add the transaction to the known transaction broadcast pile regardless of validity, to eliminate network reverb.
                                 * Future versions will have better management of broadcast retention pools by checking for probable usefulness and not rebroadcasting
                                 * known-useless transactions, such as those with indexes behind their current signature index, or ones that don't validate correctly.
                                 */
                                allBroadcastTransactions.add(parts[1]);
                                pendingTransactionContainer.addTransaction(parts[1]);
                                if (TransactionUtility.isTransactionValid(parts[1]))
                                {
                                    System.out.println("New transaction on network:");
                                    String[] transactionParts = parts[1].split(";");
                                    for (int k = 2; k < transactionParts.length - 2; k+=2)
                                    {
                                        System.out.println("     " + transactionParts[k + 1] + " curecoin from " + transactionParts[0] + " to " + transactionParts[k]);
                                    }
                                    System.out.println("Total curecoin sent: " + transactionParts[1]);
                                    peerNetwork.broadcast("TRANSACTION " + parts[1]);
                                }
                                else
                                {
                                    System.out.println("Not a good transaction. Forwarding anyway cause this is 2.0.0a1");
                                    peerNetwork.broadcast("TRANSACTION " + parts[1]);
                                }
                            }
                        }
                        else if (parts[0].equalsIgnoreCase("PEER"))
                        {
                            /*
                             * Peer discovery mechanisms are currently limited. 
                             */
                            boolean exists = false;
                            for (int k = 0; k < peers.size(); k++)
                            {
                                if (peers.get(k).equals(parts[1] + ":" + parts[2]))
                                {
                                    exists = true;
                                }
                            }
                            if (!exists)
                            {
                                try
                                {
                                    peerNetwork.connectToPeer(parts[1].substring(0, parts[1].indexOf(":")), Integer.parseInt(parts[1].substring(parts[1].indexOf(":") + 1)));
                                    peers.add(parts[1]);
                                    PrintWriter out = new PrintWriter(peerFile);
                                    for (int k = 0; k < peers.size(); k++)
                                    {
                                        out.println(peers.get(k));
                                    }
                                    out.close();
                                } catch (Exception e)
                                {
                                    System.out.println("PEER COMMUNICATED INVALID PEER!");
                                }
                            }
                        }
                        else if (parts[0].equalsIgnoreCase("GET_PEER"))
                        {
                            /*
                             * Returns a random peer host/port combo to the querying peer.
                             * Future versions will detect dynamic ports and not send peers likely to not support direct connections.
                             * While not part of GET_PEER, very-far-in-the-future-versions may support TCP punchthrough assists.
                             */
                            Random random = new Random();
                            peerNetwork.peerThreads.get(i).outputThread.write("PEER " + peers.get(random.nextInt(peers.size())));
                        }
                        else if (parts[0].equalsIgnoreCase("GET_BLOCK"))
                        {
                            try
                            {
                                Block block = databaseMaster.getBlock(Integer.parseInt(parts[1]));
                                if (block != null)
                                {
                                    System.out.println("Sending block " + parts[1] + " to peer...");
                                    peerNetwork.peerThreads.get(i).outputThread.write("BLOCK " + block.getRawBlock());
                                }
                            } catch (Exception e)
                            {
                            }
                        }
                    }
                }
            }
            int currentChainHeight = databaseMaster.getBlockchainLength();
            /*
             * Current chain is shorter than peer chains. Chain starts counting at 0, so a chain height of 15, for example, means there are 15 blocks, and the top block's index is 14.
             */
            if (topBlock > currentChainHeight)
            {
                System.out.println("currentChainHeight: " + currentChainHeight);
                System.out.println("topBlock: " + topBlock);
                for (int i = currentChainHeight; i < topBlock; i++) //Broadcast request for new block(s)
                {
                    System.out.println("Requesting block " + i + "...");
                    peerNetwork.broadcast("GET_BLOCK " + i);
                }
            }
            /*
             * Loop through all of the rpcAgent rpcThreads looking for new queries. Note that setting the response to a string twice in response to one command will cause queue issues.
             * This may be changed in a later version, but I want to keep the RPCServer elements light on memory with less moving parts--they shouldn't be a point of failure.
             * Keeping with only one String allowed in the output queue (instead of the ArrayList<String> model employed by the P2P networking functions) is simplistic for now.
             */
            for (int i = 0; i < rpcAgent.rpcThreads.size(); i++)
            {
                String request = rpcAgent.rpcThreads.get(i).request;
                if (request != null)
                {
                    String[] parts = request.split(" ");
                    parts[0] = parts[0].toLowerCase();
                    if (parts[0].equals("getbalance"))
                    {
                        if (parts.length > 1)
                        {
                            rpcAgent.rpcThreads.get(i).response = databaseMaster.getAddressBalance(parts[1]) + ""; //Turn it into a String
                        }
                        else
                        {
                            rpcAgent.rpcThreads.get(i).response = databaseMaster.getAddressBalance(addressManager.getDefaultAddress()) + "";
                        }
                    }
                    else if (parts[0].equals("getinfo"))
                    {
                        /*
                         * getinfo will be expanded in the future to give a lot more information.
                         * This is the bare minimum required to function for debugging.
                         */
                        String response = "Blocks: " + databaseMaster.getBlockchainLength();
                        response += "\nLast block hash: " + databaseMaster.getBlock(databaseMaster.getBlockchainLength() - 1).blockHash;
                        response += "\nDifficulty: " + databaseMaster.getDifficulty();
                        response += "\nMain address: " + addressManager.getDefaultAddress();
                        response += "\nMain address balance: " + databaseMaster.getAddressBalance(addressManager.getDefaultAddress());
                        rpcAgent.rpcThreads.get(i).response = response;
                    }
                    else if (parts[0].equals("send"))
                    {
                        try
                        {
                            long amount = Long.parseLong(parts[1]);
                            String destinationAddress = parts[2];
                            String address = addressManager.getDefaultAddress();
                            String fullTransaction = addressManager.getSignedTransaction(destinationAddress, amount, databaseMaster.getAddressSignatureIndex(address) + addressManager.getDefaultAddressIndexOffset());
                            addressManager.incrementDefaultAddressIndexOffset();
                            System.out.println("Attempting to verify transaction... " + TransactionUtility.isTransactionValid(fullTransaction));
                            pendingTransactionContainer.addTransaction(fullTransaction);
                            peerNetwork.broadcast("TRANSACTION " + fullTransaction);
                            System.out.println("Sending " + amount + " from " + address + " to " + destinationAddress);
                            rpcAgent.rpcThreads.get(i).response = "Sent " + amount + " from " + address + " to " + destinationAddress;
                        } catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                    }
                    else if (parts[0].equals("submittx"))
                    {
                        if (TransactionUtility.isTransactionValid(parts[1]))
                        {
                            pendingTransactionContainer.addTransaction(parts[0]);
                            peerNetwork.broadcast("TRANSACTION " + parts[1]);
                            rpcAgent.rpcThreads.get(i).response = "Sent raw transaction!";
                        }
                    }
                    else if (parts[0].equals("submitcert"))
                    {
                        rpcAgent.rpcThreads.get(i).request = null;
                        /*
                         * We have seven things to do:
                         * 1.) Check certificate for all nonces
                         * If 1. shows a difficulty above the network difficulty (below the target), proceed with creating a block:
                         * 2.) Gather all transactions from the pending transaction pool. Test all for validity. Test all under a max balance test.
                         * 3.) Put correct transactions in any arbitrary order, except for multiple transactions from the same address, which are ordered by signature index.
                         * 4.) Input the ledger hash (In 2.0.0a1, this is 0000000000000000000000000000000000000000000000000000000000000000, as ledger hashing isn't fully implemented)
                         * 5.) Hash the block
                         * 6.) Sign the block
                         * 7.) Return full block
                         * Steps 5, 6, and 7 are handled outside of MainClass, by a static method inside BlockGenerator.
                         */
                        //First, we'll check for the max difficulty. 
                        Certificate certificate = new Certificate(parts[1]);
                        String[] scoreAndNonce = certificate.getMinCertificateScoreWithNonce().split(":");
                        int bestNonce = Integer.parseInt(scoreAndNonce[0]);
                        long lowestScore = Long.parseLong(scoreAndNonce[1]);
                        long target = Long.MAX_VALUE/(databaseMaster.getDifficulty()/2); //Difficulty and target have an inverse relationship.
                        if (lowestScore < target)
                        {
                            //Great, certificate is a winning certificate!
                            //Gather all of the transactions from pendingTransactionContainer, check them.
                            ArrayList<String> allPendingTransactions = pendingTransactionContainer.pendingTransactions;
                            System.out.println("Inital pending pool size: " + allPendingTransactions.size());
                            allPendingTransactions = TransactionUtility.sortTransactionsBySignatureIndex(allPendingTransactions);
                            System.out.println("Pending pool size after sorting: " + allPendingTransactions.size());
                            //All transactions have been ordered, and tested for validity. Now, we need to check account balances to make sure transactions are valid. 
                            //As all transactions are grouped by address, we'll check totals address-by-address
                            ArrayList<String> finalTransactionList = new ArrayList<String>();
                            for (int j = 0; j < allPendingTransactions.size(); j++)
                            {
                                String transaction = allPendingTransactions.get(j);
                                String address = transaction.split(";")[0];
                                //Begin at 0L, and add all outputs to exitBalance
                                long exitBalance = 0L;
                                long originalBalance = databaseMaster.getAddressBalance(address);
                                //Used to keep track of the offset from j while still working on the same address, therefore not going through the entire for-loop again
                                int counter = 0;
                                //Previous signature count for an address--in order to ensure transactions use the correct indices
                                long previousSignatureCount = databaseMaster.getAddressSignatureIndex(address);
                                boolean foundNewAddress = false;
                                while (!foundNewAddress && j + counter < allPendingTransactions.size())
                                {
                                    transaction = allPendingTransactions.get(j + counter);
                                    if (!address.equals(transaction.split(";")[0]))
                                    {
                                        foundNewAddress = true;
                                        address = transaction.split(";")[0];
                                        j = j + counter;
                                    }
                                    else
                                    {
                                        exitBalance += Long.parseLong(transaction.split(";")[1]); //Element at index 1 (2nd element) is the full output amount!
                                        if (exitBalance <= originalBalance && previousSignatureCount + 1 == Long.parseLong(transaction.split(";")[transaction.split(";").length - 1])) //Transaction looks good!
                                        {
                                            //Add seemingly-good transaction to the list, and increment previousSignatureCount for signature order assurance. 
                                            finalTransactionList.add(transaction);
                                            System.out.println("While making block, added transaction " + transaction);
                                            previousSignatureCount++;
                                        }
                                        else
                                        {
                                            System.out.println("Transaction failed final validation...");
                                            System.out.println("exitBalance: " + exitBalance);
                                            System.out.println("originalBalance: " + originalBalance);
                                            System.out.println("previousSignatureCount: " + previousSignatureCount);
                                            System.out.println("signature count of new tx: " + Long.parseLong(transaction.split(";")[transaction.split(";").length - 1]));
                                        }
                                        //Counter keeps track of the sub-2nd-layer-for-loop incrementation along the ArrayList. It's kinda 3D.
                                        counter++;
                                    }
                                }
                            }
                            //We have the transaction list; now we need to assemble the block. I moved this code into its own method, because it would be ugly here. That method handles steps 5, 6, and 7.
                            //databaseMaster.getBlockchainLength() doesn't have one added to it to account for starting from 0!
                            String fullBlock = BlockGenerator.compileBlock(System.currentTimeMillis(), databaseMaster.getBlockchainLength(), databaseMaster.getLatestBlock().blockHash, databaseMaster.getLatestBlock().difficulty, bestNonce, "0000000000000000000000000000000000000000000000000000000000000000", finalTransactionList, certificate, certificate.redeemAddress, addressManager.getDefaultPrivateKey(), databaseMaster.getAddressSignatureIndex(certificate.redeemAddress));
                            //We finally have the full block. Now to submit it to ourselves...
                            Block toAdd = new Block(fullBlock);
                            boolean success = databaseMaster.addBlock(toAdd);
                            if (success) //The block appears legitimate to ourselves! Send it to others!
                            {
                                System.out.println("Block added to network successfully!");
                                peerNetwork.broadcast("BLOCK " + fullBlock);
                                pendingTransactionContainer.reset(); //Any transactions left in pendingTransactionContainer that didn't get submitted into the block should be cleared anyway--they probably aren't valid for some reason, likely balance issues.
                                addressManager.resetDefaultAddressIndexOffset();
                            }
                            else
                            {
                                System.out.println("Block was not added successfully! :(");
                            }
                            rpcAgent.rpcThreads.get(i).response = "Successfully submitted block! \nCertificate earned target score " + lowestScore + "\nWhich is below target " + target;
                        }
                        else
                        {
                            rpcAgent.rpcThreads.get(i).response = "Certificate failed with target score " + lowestScore + "\nWhich is above target " + target;
                        } 
                    }
                    else
                    {
                        rpcAgent.rpcThreads.get(i).response = "Unknown command \"" + parts[0] + "\"";
                    }
                }
            }
            try
            {
                Thread.sleep(100);
            } catch (Exception e) {}
        }
    }

    public static void launch()
    {
        Console console = System.console(); //Get a system console object
        if (console != null) //If the application has a console
        {
            File f = new File("launch.bat");
            if (f.exists())
            {
                //f.delete(); //delete bat file if it exists
            }
        } 
        else if (!GraphicsEnvironment.isHeadless()) //Application doesn't have a console, let's give it one!
        {
            String os = System.getProperty("os.name").toLowerCase(); //Get OS
            if (os.contains("indows")) //If OS is a windows OS
            { 
                try
                {
                    File JarFile = new File(MainClass.class.getProtectionDomain().getCodeSource().getLocation().toURI());//Get the absolute location of the .jar file
                    PrintWriter out = new PrintWriter(new File("launch.bat")); //Get a PrintWriter object to make a batch file
                    out.println("@echo off"); //turn echo off for batch file
                    out.println("title Curecoin 2.0.0a1"); 
                    out.println("java -Xmx500M -jar \"" + JarFile.getPath() + "\"");
                    out.println("start /b \"\" cmd /c del \"%~f0\"&exit /b");
                    out.close(); //saves file
                    Runtime rt = Runtime.getRuntime(); //gets runtime
                    rt.exec("cmd /c start launch.bat"); //executes batch file
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
                System.exit(0); //Exit program, so only instance of program with command line runs!
            }
        }
    }
}
