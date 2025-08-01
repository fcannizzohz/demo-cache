package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.fcannizzohz.democache.model.Product;
import com.hazelcast.map.IMap;

import java.util.Set;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();

        IMap<Integer, Product> products = client.getMap("products");
        Set<Integer> keys = products.keySet();
        System.out.println("products map: " + products.keySet());
        for (Integer key : keys) {
            Product product = products.get(key);
            System.out.println(product);
        }
        client.shutdown();
    }
}