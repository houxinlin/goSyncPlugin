package dev.coolrequest.gosync.ssh;

import java.util.List;

public interface IFileComparison {
    public List<String> compareFiles(List<String> uploadFiles, String targetDir) throws Exception;
}
