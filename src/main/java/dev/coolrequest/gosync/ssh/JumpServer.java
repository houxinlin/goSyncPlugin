package dev.coolrequest.gosync.ssh;

import com.jcraft.jsch.ChannelShell;
import dev.coolrequest.gosync.GoSyncTask;
import dev.coolrequest.gosync.LrzszUtils;
import dev.coolrequest.gosync.MD5Util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JumpServer implements IService {
    private final HostInfo hostInfo;
    private final GoSyncTask gsTask;

    public JumpServer(HostInfo hostInfo, GoSyncTask gsTask) {
        this.hostInfo = hostInfo;
        this.gsTask = gsTask;
    }

    @Override
    public List<String> compareFiles(List<String> uploadFiles, String targetDir) throws Exception {
        Map<String, String> map = new HashMap<>();
        for (String uploadFile : uploadFiles) {
            File file = new File(uploadFile);
            map.put(file.getName(), MD5Util.getFileMD5(file));
        }
        SSH ssh;
        ssh = getSSH(hostInfo, gsTask);
        String uuid = UUID.randomUUID().toString();
        String command = String.format(
                "mkdir -p \"%1$s\"; cd \"%1$s\"; md5sum * /dev/null 2>/dev/null; echo %2$s",
                targetDir, uuid
        );
        ssh.getSshOut().write((command + "\r").getBytes(StandardCharsets.UTF_8));
        ssh.getSshOut().flush();
        Thread.sleep(500);
        StringBuilder sb = new StringBuilder();
        while (true) {
            byte[] buffer = new byte[1024];
            int read = ssh.getSshIn().read(buffer);
            sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            int i1 = sb.indexOf(uuid);
            int i2 = sb.lastIndexOf(uuid);
            if ((i1 != -1 && i2 != -1) && i1 < i2) {
                break;
            }
        }
        try (BufferedReader reader = new BufferedReader(new StringReader(sb.toString()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] part = line.split("  ");
                if (part.length != 2) continue;
                if (map.containsKey(part[1]) && map.get(part[1]).equals(part[0])) {
                    map.remove(part[1]);
                }
            }

        } catch (Exception e) {
            throw e;
        } finally {
            ssh.disconnect();
        }
        return uploadFiles.stream().filter(s -> map.containsKey(new File(s).getName())).collect(Collectors.toList());

    }

    private static class WaitRZResponse implements Runnable {
        private final InputStream inputStream;
        private final CountDownLatch countDownLatch;

        public WaitRZResponse(InputStream inputStream, CountDownLatch countDownLatch) {
            this.inputStream = inputStream;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[4096];
                while (true) {
                    int read = inputStream.read(buffer);
                    if (read == -1) {
                        throw new IOException();
                    }
                    String data = new String(buffer, 0, read);
                    if (data.contains("**\u0018B0100000023be50")) {
                        countDownLatch.countDown();
                        return;
                    }
                }
            } catch (Exception ignored) {

            }
        }
    }

    private boolean doUploadFile(InputStream sshInputStream, OutputStream sshOutputStream, String file, String targetDir) throws IOException {
        String rzCommand = String.format("mkdir -p %s && cd %s && %srz -b -y\r",
                targetDir,
                targetDir,
                "");
        sshOutputStream.write((rzCommand + "\r").getBytes(StandardCharsets.UTF_8));
        sshOutputStream.flush();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Thread thread = new Thread(new WaitRZResponse(sshInputStream, countDownLatch));
        thread.start();
        try {
            if (countDownLatch.await(5, TimeUnit.SECONDS)) {
                return startSzTransfer(file, sshOutputStream, sshInputStream);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return false;

    }
    private boolean startSzTransfer(String file, OutputStream sshOutputStream, InputStream sshIntputStream) throws Exception {
        String[] command = buildSzCommand(new File(file).getName());
        Process szProcess = new ProcessBuilder()
                .directory(new File(file).getParentFile())
                .command(command).start();
        InputStream szIn = szProcess.getInputStream();
        OutputStream szOut = szProcess.getOutputStream();
        Transfer transfer = Transfer.create(szIn, sshOutputStream, file, createProgressListener());

        Thread thread = new Thread(transfer);
        thread.start();
        try {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = sshIntputStream.read(buffer, 0, 1024)) >= 0) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                szOut.write(buffer, 0, read);
                szOut.flush();
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) return false;
        }
        return checkResult(sshIntputStream, sshOutputStream, file);
    }

    private static int getCRCount(byte[] bytes) {
        int count = 0;
        for (int i = 0; i < bytes.length - 1; i++) {
            if ((bytes[i] & 0xFF) == 0x0D && (bytes[i + 1] & 0xFF) == 0x0A) {
                count++;
                i++;
            }
        }
        return count;
    }

    private boolean checkResult(InputStream inputStream, OutputStream outputStream, String filePath) throws Exception {
        byte[] buffer = new byte[4096];
        File file = new File(filePath);
        String fileMD5 = MD5Util.getFileMD5(file);
        outputStream.write(("md5sum " + file.getName() + "\r").getBytes());
        outputStream.flush();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, read);
            if (byteArrayOutputStream.toString().contains(file.getName()) && getCRCount(byteArrayOutputStream.toByteArray()) == 2) {
                return byteArrayOutputStream.toString().contains(fileMD5);
            }
        }
        return false;
    }

    private String[] buildSzCommand(String path) {
        String executePath = LrzszUtils.getExecutePath();
        if (executePath != null) {
            return new String[]{executePath, "-b", path};
        }
        String[] commonPaths = {
                "/opt/homebrew/bin/sz",
                "/usr/local/bin/sz",
                "/usr/bin/sz",
                "/opt/local/bin/sz",
                "/usr/local/lrzsz/sz",
                "/usr/bin/lrzsz-sz",
                "/usr/local/bin/lrzsz-sz",
                "sz"
        };

        for (String szPath : commonPaths) {
            try {
                Process testProcess = new ProcessBuilder(szPath, "--version").start();
                int exitCode = testProcess.waitFor();
                if (exitCode == 0 || exitCode == 1) {
                    return new String[]{szPath, "-b", path};
                }
            } catch (Exception ignored) {
            }
        }
        return new String[]{"/usr/local/bin/sz", "-b", path};
    }

    private Transfer.ProgressListener createProgressListener() {
        return new Transfer.ProgressListener() {
            @Override
            public void onProgress(float progress) {
            }

            @Override
            public void onSuccess() {

            }

            @Override
            public void onComplete(Exception exception) {

            }
        };
    }



    @Override
    public boolean transport(String filePath, String targetDir) throws Exception {
        SSH ssh = null;
        try {
            ssh = getSSH(hostInfo, gsTask);
            return doUploadFile(ssh.getSshIn(), ssh.getSshOut(), filePath, targetDir);
        } finally {
            if (ssh != null) {
                ssh.disconnect();
            }
        }
    }

    private static SSH getSSH(HostInfo hostInfo, GoSyncTask gsTask) throws Exception {
        ChannelShell channelShell = JschFactory.openChannel(hostInfo);
        InputStream inputStream = channelShell.getInputStream();
        OutputStream outputStream = channelShell.getOutputStream();
        String s = gsTask.getAfterConnectedCommand().get();
        String[] split = s.split("\r");
        for (String string : split) {
            outputStream.write((string + "\r").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            Thread.sleep(500);
        }
        Thread.sleep(1500);
        String uuid = UUID.randomUUID().toString();
        String echo = "echo " + uuid;
        outputStream.write((echo + "\r").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        StringBuilder buffer = new StringBuilder();
        byte[] readBuffer = new byte[1024];
        String prompt = null;
        while (true) {
            int read = inputStream.read(readBuffer);
            if (read > 0) {
                String data = new String(readBuffer, 0, read, StandardCharsets.UTF_8);
                buffer.append(data);
                int index = buffer.indexOf(uuid);
                if (index != -1) {
                    String[] parts = buffer.substring(index).split("\r\n");
                    if (parts.length == 3 && parts[1].equals(parts[0])) {
                        prompt = parts[2].trim();
                        break;
                    }

                }
            }
        }
        return new SSH(channelShell, outputStream, inputStream, prompt);

    }

    private static class SSH {
        private OutputStream sshOut;
        private InputStream sshIn;
        private String prompt;
        private final ChannelShell channelShell;

        public SSH(ChannelShell shell, OutputStream sshOut, InputStream sshIn, String prompt) {
            this.sshOut = sshOut;
            this.sshIn = sshIn;
            this.prompt = prompt;
            this.channelShell = shell;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }

        public OutputStream getSshOut() {
            return sshOut;
        }

        public void setSshOut(OutputStream sshOut) {
            this.sshOut = sshOut;
        }

        public InputStream getSshIn() {
            return sshIn;
        }

        public void setSshIn(InputStream sshIn) {
            this.sshIn = sshIn;
        }

        public void disconnect() {
            if (channelShell != null) {
                channelShell.disconnect();
            }
        }
    }
}
