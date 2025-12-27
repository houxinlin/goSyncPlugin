package dev.coolrequest.gosync.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpProgressMonitor;
import dev.coolrequest.gosync.GoSyncTask;
import dev.coolrequest.gosync.MD5Util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SimpleServer implements IService {
    private final HostInfo hostInfo;
    private final GoSyncTask gsTask;

    public SimpleServer(HostInfo hostInfo, GoSyncTask gsTask) {
        this.hostInfo = hostInfo;
        this.gsTask = gsTask;
    }

    @Override
    public List<String> compareFiles(List<String> uploadFiles, String targetDir) throws Exception {
        if (uploadFiles == null || uploadFiles.isEmpty())
            return Collections.emptyList();

        Map<String, String> localMd5Map = new HashMap<>();
        for (String path : uploadFiles) {
            File file = new File(path);
            localMd5Map.put(file.getName(), MD5Util.getFileMD5(file));
        }

        String command = String.format(
                "mkdir -p %1$s && cd %1$s && if [ -n \"$(ls -A 2>/dev/null)\" ]; then md5sum *; fi",
                targetDir);
        String output = executeRemoteCommand(command);
        if (!output.isEmpty()) {
            try (BufferedReader reader = new BufferedReader(new StringReader(output))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 2) {
                        String md5 = parts[0];
                        String fileName = parts[1];
                        if (localMd5Map.containsKey(fileName) && localMd5Map.get(fileName).equalsIgnoreCase(md5)) {
                            localMd5Map.remove(fileName);
                        }
                    }
                }
            }
        }
        return uploadFiles.stream()
                .filter(path -> localMd5Map.containsKey(new File(path).getName()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean transport(String filePath, String targetDir) throws Exception {
        ChannelSftp sftp = null;
        try {
            sftp = JschFactory.openSFTP(hostInfo);
            File localFile = new File(filePath);
            String fileName = localFile.getName();
            FileInputStream fis = new FileInputStream(filePath);
            sftp.put(fis, Paths.get(targetDir, fileName).toString().replace("\\", "/"), new SftpProgressMonitor() {
                @Override
                public void init(int op, String src, String dest, long max) {
                }

                @Override
                public boolean count(long count) {
                    return true;
                }

                @Override
                public void end() {

                }
            });
            String remoteMd5 = getRemoteFileMd5(targetDir, fileName);
            String localMd5 = MD5Util.getFileMD5(localFile);
            return localMd5.equalsIgnoreCase(remoteMd5);
        } finally {
            if (sftp != null) {
                if (sftp.getSession() != null)
                    sftp.getSession().disconnect();
                sftp.disconnect();
            }
        }
    }

    @Override
    public String execCommand(String command) throws Exception {
        return executeRemoteCommand(command);
    }

    public String executeRemoteCommand(String command) throws Exception {
        ChannelExec channelExec = null;
        try {
            channelExec = JschFactory.openExecChannel(hostInfo, command);
            InputStream in = channelExec.getInputStream();
            InputStream err = channelExec.getErrStream();
            channelExec.connect(3000);
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[2048];
            while (true) {
                while (in.available() > 0) {
                    int read = in.read(buffer, 0, 2048);
                    if (read < 0)
                        break;
                    sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
                while (err.available() > 0) {
                    int read = err.read(buffer, 0, 2048);
                    if (read < 0)
                        break;
                    sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
                if (channelExec.isClosed()) {
                    if (in.available() <= 0 && err.available() <= 0) {
                        break;
                    }
                }
                Thread.sleep(50);
            }
            return sb.toString();
        } finally {
            if (channelExec != null) {
                if (channelExec.getSession() != null) {
                    channelExec.getSession().disconnect();
                }
                channelExec.disconnect();
            }
        }
    }

    private String getRemoteFileMd5(String dir, String fileName) throws Exception {
        String remotePath = Paths.get(dir, fileName).toString().replace("\\", "/");

        String command = String.format("if [ -f \"%1$s\" ]; then md5sum \"%1$s\"; fi", remotePath);
        String output = executeRemoteCommand(command);
        if (!output.isEmpty()) {
            String[] parts = output.trim().split("\\s+");
            if (parts.length >= 1) {
                return parts[0];
            }
        }
        return "";
    }

}