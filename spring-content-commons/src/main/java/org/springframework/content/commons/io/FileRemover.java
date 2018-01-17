package org.springframework.content.commons.io;

import java.io.File;

public class FileRemover implements CloseableObserver {

    private File file;

    public FileRemover(File file) {
        this.file = file;
    }


    @Override
    public void closed() {
        this.file.delete();
    }
}
