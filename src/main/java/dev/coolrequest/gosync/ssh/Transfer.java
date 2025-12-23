package dev.coolrequest.gosync.ssh;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class Transfer implements Runnable {
    public InputStream szSourceInputStream;
    private final OutputStream outTo;
    private ProgressListener progressListener;
    private final String file;

    private Transfer(InputStream szSourceInputStream, OutputStream outTo, String file) {
        this.szSourceInputStream = szSourceInputStream;
        this.outTo = outTo;
        this.file = file;
    }

    public static Transfer create(InputStream source, OutputStream outTo, String file, ProgressListener progressListener) {
        Transfer transfer = new Transfer(source, outTo, file);
        transfer.setProgressListener(progressListener);
        return transfer;
    }

    public void setProgressListener(ProgressListener progressListener) {
        this.progressListener = progressListener;
    }

    public static int findBytePositions(byte[] data, byte target) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public void transferTo() throws IOException {
        byte[] szFileDataPacakge = new byte[]{
                (byte) 0x2A, (byte) 0x18, (byte) 0x43, (byte) 0x0A
        };

        long sendTotal = 0;
        long size = Files.size(Paths.get(file));
        byte[] buffer = new byte[1024];
        int read;
        ByteArrayOutputStream cache = null;
        while ((read = szSourceInputStream.read(buffer)) > 0) {
            outTo.write(buffer, 0, read);
            outTo.flush();
            try {
                if (startsWith(buffer, szFileDataPacakge)) {
                    if (read>= 12) {
                        cache = new ByteArrayOutputStream();
                        cache.write(buffer, 12, read - 12);
                    }
                } else {
                    if (cache != null) {
                        cache.write(buffer, 0, read);
                    }
                }
                if (cache != null) {
                    byte[] byteArray = cache.toByteArray();
                    long nextPackSize = size - sendTotal >= 1024 ? 1024 : (size - sendTotal);
                    nextPackSize += 6;
                    while (sendTotal != size && byteArray.length >= nextPackSize) {
                        sendTotal += nextPackSize - 6;
                        cache.reset();
                        cache.write(Arrays.copyOfRange(byteArray, (int) nextPackSize, byteArray.length));
                        byteArray = cache.toByteArray();
                        float progress = (((float) sendTotal) / size);
                        nextPackSize = size - sendTotal >= 1024 ? 1024 : (size - sendTotal);
                        nextPackSize += 6;
                        progressListener.onProgress(progress);
                    }
                }
            } catch (Exception ignored) {

            }
        }
    }

    @Override
    public void run() {
        try {
            this.transferTo();
        } catch (Exception e) {
            progressListener.onComplete(e);
        }
    }

    public static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    public interface ProgressListener {
        void onProgress(float progress);
        void onSuccess();
        void onComplete(Exception exception);
    }
}
