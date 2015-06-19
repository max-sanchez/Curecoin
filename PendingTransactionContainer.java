/*
 * Curecoin 2.0.0a Source Code
 * Copyright (c) 2015 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

import java.util.*;

/**
 * This class offers basic functionality for storing transactions until they make it into a block.
 * It could be just an ArrayList<String> inside of MainClass, however it seemed easier and more OOP-ish to give it its own object.
 * Adding future functionality to pending transaction pool management is much easier when it has its own object. 
 */
public class PendingTransactionContainer
{
    public ArrayList<String> pendingTransactions;

    /**
     * Constructor for PendingTransactionContainer sets up required ArrayList for holding transactions
     */
    public PendingTransactionContainer()
    {
        this.pendingTransactions = new ArrayList<String>();
    }

    /**
     * Adds a transaction to the pending transaction list if it is formatted correctly and accompanied by a correct signature. Does not check for account balances!
     * Rejects duplicate transactions.
     * Transaction format: 
     * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
     * 
     * @param transaction Transaction to add
     * 
     * @return boolean Whether adding the transaction was valid
     */
    public boolean addTransaction(String transaction)
    {
        for (int i = 0; i < pendingTransactions.size(); i++)
        {
            if (pendingTransactions.get(i).equals(transaction))
            {
                return false;
            }
        }
        MerkleAddressUtility merkleAddressUtility = new MerkleAddressUtility();
        if (!TransactionUtility.isTransactionValid(transaction))
        {
            return false;
        }
        pendingTransactions.add(transaction);
        System.out.println("Added transaction " + transaction.substring(0, 20) + "..." + transaction.substring(transaction.length() - 20, transaction.length()));
        return true;
    }

    /**
     * Self-explanatory method called whenever the daemon desires to reset the pending transaction pool to be blank.
     */
    public void reset()
    {
        pendingTransactions = new ArrayList<String>();
    }

    /**
     * Removes an identical transaction from the pending transactions pool
     * 
     * @param transaction The transaction to remove
     * 
     * @return boolean Whether removal was successful
     */
    public boolean removeTransaction(String transaction)
    {
        for (int i = 0; i < pendingTransactions.size(); i++)
        {
            if (pendingTransactions.get(i).equals(transaction))
            {
                pendingTransactions.remove(i);
                return true;
            }
        }
        return false; //Transaction was not found in pending transaction pool
    }

    /**
     * This method is the most useful method in this class--it allows the mass removal of all transactions from the pending transaction pool that were included
     * in a network block, all in one call. The returned boolean is not currently utilized in MainClass, proper handling of blocks with transaction issues will be addressed
     * in a future alpha, probably 2.0.0a3 given my schedule.
     * 
     * @param rawBlock The raw String representing the block holding transactions to remove
     * 
     * @return boolean Whether all transactions in the block were successfully removed
     */
    public boolean removeTransactionsInBlock(String rawBlock)
    {
        //This try-catch method wraps around more than it needs to, in the name of easy code management, and making colors line up nicely in my IDE.
        try
        {
            //We could use the raw String data, but it's easier to use a Block object to avoid repetition of code, and the verification is an added bonus.
            Block tempBlock = new Block(rawBlock);
            /* Transaction format: 
             * InputAddress;InputAmount;OutputAddress1;OutputAmount1;OutputAddress2;OutputAmount2...;SignatureData;SignatureIndex
             * 
             * We are removing only transactions that match the exact String from the block. If the block validation fails, NO transactions are removed from the pool.
             * In a late-night coding session, not removing any transactions of an invalid block seemed like the bset idea--transactions should never be discarded
             * if they haven't made it into the blockchain, and any block that doesn't validate won't make it through Blockchain's block screening, so these transactions
             * that we aren't removing will never happen on-chain if we remove them from the pool when an invalid block says we should. Also closes a potential attack
             * vector where someone could submit false blocks in order to be a nuisance and empty the pending transaction pool.
             */
            if (!tempBlock.validateBlock())
            {
                return false; //No transactions remove at all!
            }
            ArrayList<String> transactions = tempBlock.transactions;
            boolean allSuccessful = true;
            for (int i = 0; i < transactions.size(); i++)
            {
                if (!removeTransaction(transactions.get(i)))
                {
                    allSuccessful = false; //This might happen if a transaction was in a block before it made it across the network to a peer, so not always a big deal!
                }
            }
            return allSuccessful;
        } catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }
}
