package com.oceanbase.obkv;

import site.ycsb.Client;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunMain {
    public static void main(String[] args) {
        try {
            List<String> list = new ArrayList<>();
            list.add("-s");
            list.add("-db");
            list.add("com.oceanbase.obkv.ycsb.OBHBaseClient");
            list.addAll(Arrays.asList(args));
            String[] arr = list.toArray(new String[0]);
            Method method = Client.class.getMethod("main", String[].class);
            method.invoke(null, new Object[]{arr});
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
