/*
 * Curecoin 2.0.0a Source Code
 * Copyright (c) 2015 Curecoin Developers
 * Distributed under MIT License
 * Requires Apache Commons Library
 * Supports Java 1.7+
 */

import java.io.*;
import java.util.*;

public class TestLedgerManager
{
    public static Random random = new Random();
    public static String[] prefixes = new String[]{"C1", "C2", "C3", "C4", "C5"};
    public static String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    public static void main(String[] args)
    {
        try 
        {
            /*File f = new File("accountdb.txt");
            PrintWriter out = new PrintWriter(f);
            out.println("C3SH5GCPI7YTA7BBQYE3BYSHLPXCQEASP6ASJQ:10:1");
            out.println("C57UBW646LTOF4WLU3SDCKV7NVTY5XPGSNN2DD:19494:13");
            out.close(); */
            LedgerManager addy = new LedgerManager("accountdb.txt");
            addy.adjustAddressSignatureCount("C3SH5GCPI7YTA7BBQYE3BYSHLPXCQEASP6ASJQ", 17);
            long tick = System.currentTimeMillis();
            for (int i = 0; i < 5000; i++)
            {
                if (i % 1000 == 0)
                {
                    System.out.println(i + " and took " + (System.currentTimeMillis() - tick));
                    tick = System.currentTimeMillis();
                }
                String address = getRandomAddress();
                addy.adjustAddressBalance(address, random.nextInt(100000));
            }
            addy.writeToFile();
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static String getRandomAddress()
    {
        String address = "";
        address += prefixes[random.nextInt(5)];
        for (int i = 0; i < 36; i++)
        {
            address += charSet.charAt(random.nextInt(32));
        }
        return address;
    }
}
