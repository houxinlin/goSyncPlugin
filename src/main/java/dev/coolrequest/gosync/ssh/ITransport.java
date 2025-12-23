package dev.coolrequest.gosync.ssh;

public interface ITransport {
    public boolean transport(String filePath,String targetDir) throws Exception;
}
