package dev.coolrequest.gosync.ssh;

import dev.coolrequest.gosync.GoSyncTask;

public interface IService extends ITransport, IFileComparison, ICommandExec {
    static IService create(String type, HostInfo hostInfo, GoSyncTask gsTask) {
        if ("simple".equals(type)) return new SimpleServer(hostInfo, gsTask);
        if ("jumpserver".equals(type)) return new JumpServer(hostInfo, gsTask);
        throw new IllegalArgumentException("Unknown server type: " + type);
    }
}
