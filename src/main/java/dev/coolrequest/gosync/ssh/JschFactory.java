package dev.coolrequest.gosync.ssh;

import com.jcraft.jsch.*;

public class JschFactory {
    public static ChannelShell openChannel(HostInfo hostInfo) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(hostInfo.getUsername(), hostInfo.getHost(), hostInfo.getPort());
        session.setPassword(hostInfo.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        ChannelShell channel = (ChannelShell) session.openChannel("shell");
        channel.setPtySize(300, 40, 0, 0);
        channel.setPty(true);
        channel.connect(3000);
        return channel;
    }

    public static ChannelExec openExecChannel(HostInfo hostInfo) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(hostInfo.getUsername(), hostInfo.getHost(), hostInfo.getPort());
        session.setPassword(hostInfo.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.connect(3000);
        return channel;
    }

    private static Session createSession(HostInfo hostInfo) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(hostInfo.getUsername(), hostInfo.getHost(), hostInfo.getPort());
        session.setPassword(hostInfo.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(5000);
        return session;
    }

    public static ChannelExec openExecChannel(HostInfo hostInfo, String command) throws Exception {
        Session session = createSession(hostInfo);
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setPtySize(300, 40, 0, 0);
        return channel;
    }

    public static ChannelSftp openSFTP(HostInfo hostInfo) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(hostInfo.getUsername(), hostInfo.getHost(), hostInfo.getPort());
        session.setPassword(hostInfo.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        ChannelSftp channel = (ChannelSftp) session.openChannel("sftp");
        channel.connect(3000);
        return channel;
    }
}
