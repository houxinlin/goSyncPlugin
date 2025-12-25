package dev.coolrequest.gosync;

import com.jcraft.jsch.JSchException;
import dev.coolrequest.gosync.ssh.HostInfo;
import dev.coolrequest.gosync.ssh.IService;

import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Sync {
    private final List<String> libs;
    private final String mainJar;
    private final GoSyncTask gsTask;

    public Sync(List<String> libs, String mainJar, GoSyncTask gsTask) {
        this.libs = libs;
        this.mainJar = mainJar;
        this.gsTask = gsTask;
    }

    private HostInfo createHostInfo() {
        HostInfo hostInfo = new HostInfo();
        hostInfo.setHost(gsTask.getServerAddress().get());
        hostInfo.setPassword(gsTask.getUserPassValue());
        hostInfo.setUsername(gsTask.getUserNameValue());
        hostInfo.setPort(gsTask.getPort().get());
        return hostInfo;
    }

    public void start() {
        HostInfo hostInfo = createHostInfo();
        ThreadPoolExecutor threadPoolExecutor =
                new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        IService iService = IService.create(gsTask.getServerType().get(), hostInfo, gsTask);
        try {
            List<String> uploadFiles = iService.compareFiles(libs, gsTask.getLibDirectory().get());
            if (uploadFiles.isEmpty()) {
                gsTask.getLogger().lifecycle("依赖无需同步");
            } else {
                gsTask.getLogger().lifecycle("共有" + uploadFiles.size() + "个依赖需要同步");
            }
            CountDownLatch countDownLatch = new CountDownLatch(uploadFiles.size());
            for (String lib : uploadFiles) {
                threadPoolExecutor.execute(new Task(lib, hostInfo, gsTask, countDownLatch));
            }
            countDownLatch.await();
            gsTask.getLogger().lifecycle(mainJar + " 上传中...");
            boolean transport = iService.transport(mainJar, gsTask.getMainJarDirectory().get());
            gsTask.getLogger().lifecycle(mainJar + " 上传" + (transport ? "成功" : "失败"));
            gsTask.getLogger().lifecycle("部署完成");
        } catch (Exception e) {
            threadPoolExecutor.shutdownNow();
            gsTask.getLogger().error("任务异常");
            if (e instanceof JSchException) {
                if (e.getMessage().contains("Auth fail")) {
                    gsTask.getLogger().error("用户名或者密码不正确,同步停止");
                }
            } else {
                e.printStackTrace();
            }
        }

    }


    private static class Task implements Runnable {
        private final HostInfo hostInfo;
        private final GoSyncTask gsTask;
        private final CountDownLatch countDownLatch;
        private final String file;

        public Task(String file, HostInfo hostInfo, GoSyncTask gsTask, CountDownLatch countDownLatch) {
            this.hostInfo = hostInfo;
            this.file = file;
            this.gsTask = gsTask;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            String name = new File(file).getName();
            try {
                gsTask.getLogger().lifecycle("上传" + name + "中...");
                IService iService = IService.create(gsTask.getServerType().get(), hostInfo, gsTask);
                boolean success = iService.transport(file, gsTask.getLibDirectory().get());
                gsTask.getLogger().lifecycle(new File(name).getName() + "上传" + (success ? "成功" : "失败"));
            } catch (Exception e) {
                gsTask.getLogger().lifecycle(name + "上传异常", e);
            } finally {
                countDownLatch.countDown();
            }
        }
    }
}
