package com.dqnt.amber;

import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;
import java.util.List;

public class Util {
    static void getFilesFromDirRecursive(List<File> list, File root) {
        File[] dir = root.listFiles();
        for (File file : dir) {
            if (file.isDirectory()) {
                getFilesFromDirRecursive(list, file);
            } else {
                list.add(file);
            }
        }
    }
}
