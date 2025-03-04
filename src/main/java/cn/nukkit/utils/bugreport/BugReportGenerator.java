package cn.nukkit.utils.bugreport;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.utils.Utils;
import com.sun.management.OperatingSystemMXBean;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Project nukkit
 */
@Log4j2
public class BugReportGenerator extends Thread {

    private Throwable throwable;

    BugReportGenerator(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public void run() {
        BaseLang baseLang = Server.getInstance().getLanguage();
        try {
            log.info("[BugReport] " + baseLang.translateString("nukkit.bugreport.create"));
            String path = generate();
            log.info("[BugReport] " + baseLang.translateString("nukkit.bugreport.archive", path));
        } catch (Exception e) {
            log.info("[BugReport] " + baseLang.translateString("nukkit.bugreport.error", e.getMessage()), e);
        }
    }

    private String generate() throws IOException {
        File reports = new File(Nukkit.DATA_PATH, "logs/bug_reports");
        if (!reports.isDirectory()) {
            reports.mkdirs();
        }

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String date = simpleDateFormat.format(new Date());

        StringBuilder model = new StringBuilder();
        long totalDiskSpace = 0;
        int diskNum = 0;
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            try {
                FileStore store = Files.getFileStore(root);
                model.append("Disk ").append(diskNum++).append(":(avail=").append(getCount(store.getUsableSpace(), true))
                        .append(", total=").append(getCount(store.getTotalSpace(), true))
                        .append(") ");
                totalDiskSpace += store.getTotalSpace();
            } catch (IOException e) {
                //
            }
        }

        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));

        StackTraceElement[] stackTrace = throwable.getStackTrace();
        boolean pluginError = false;
        if (stackTrace.length > 0) {
            pluginError = !throwable.getStackTrace()[0].getClassName().startsWith("cn.nukkit");
        }


        File mdReport = new File(reports, date + "_" + throwable.getClass().getSimpleName() + ".md");
        mdReport.createNewFile();
        String content = Utils.readFile(this.getClass().getModule().getResourceAsStream("report_template.md"));

        String cpuType = System.getenv("PROCESSOR_IDENTIFIER");
        OperatingSystemMXBean osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        content = content.replace("${NUKKIT_VERSION}", Nukkit.VERSION);
        content = content.replace("${NUKKIT_COMMIT}", Nukkit.GIT_COMMIT);
        content = content.replace("${JAVA_VERSION}", System.getProperty("java.vm.name") + " (" + System.getProperty("java.runtime.version") + ")");
        content = content.replace("${HOSTOS}", osMXBean.getName() + "-" + osMXBean.getArch() + " [" + osMXBean.getVersion() + "]");
        content = content.replace("${MEMORY}", getCount(osMXBean.getTotalPhysicalMemorySize(), true));
        content = content.replace("${STORAGE_SIZE}", getCount(totalDiskSpace, true));
        content = content.replace("${CPU_TYPE}", cpuType == null ? "UNKNOWN" : cpuType);
        content = content.replace("${AVAILABLE_CORE}", String.valueOf(osMXBean.getAvailableProcessors()));
        content = content.replace("${STACKTRACE}", stringWriter.toString());
        content = content.replace("${PLUGIN_ERROR}", Boolean.toString(pluginError).toUpperCase());
        content = content.replace("${STORAGE_TYPE}", model.toString());

        Utils.writeFile(mdReport, content);

        return mdReport.getAbsolutePath();
    }

    //Code section from SOF
    public static String getCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

}
