package de.tuberlin.aura.client.executors;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tuberlin.aura.core.common.utils.ProcessExecutor;
import de.tuberlin.aura.core.config.IConfig;
import de.tuberlin.aura.core.config.IConfigFactory;
import de.tuberlin.aura.core.zookeeper.ZookeeperClient;
import de.tuberlin.aura.taskmanager.TaskManager;
import de.tuberlin.aura.workloadmanager.WorkloadManager;

public final class LocalClusterSimulator {

    // ---------------------------------------------------
    // Execution Modes.
    // ---------------------------------------------------

    public static enum ExecutionMode {

        EXECUTION_MODE_SINGLE_PROCESS,

        EXECUTION_MODE_MULTIPLE_PROCESSES;

        static ExecutionMode get(String name) {
            switch (name) {
                case "single":
                    return EXECUTION_MODE_SINGLE_PROCESS;
                case "multiple":
                    return EXECUTION_MODE_MULTIPLE_PROCESSES;
                default:
                    throw new IllegalArgumentException("Unexpected execution mode, one of 'single' or 'multiple' expected");
            }
        }
    }

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private static final Logger LOG = LoggerFactory.getLogger(LocalClusterSimulator.class);

    private final List<TaskManager> tmList;

    private final List<ProcessExecutor> peList;

    private final ZooKeeperServer zookeeperServer;

    private final NIOServerCnxnFactory zookeeperCNXNFactory;

    // ---------------------------------------------------
    // Constructors.
    // ---------------------------------------------------

    public LocalClusterSimulator(final IConfig config) {
        final ExecutionMode mode = ExecutionMode.get(config.getString("simulator.process.mode"));
        final boolean startupZookeeper = config.getBoolean("simulator.zookeeper.startup");
        final int tickTime = config.getInt("simulator.zookeeper.tick.time");
        final int numNodes = config.getInt("simulator.tm.number");
        final int numConnections = config.getInt("simulator.connections.number");
        final String zkServer = ZookeeperClient.buildServersString(config.getObjectList("zookeeper.servers"));
        final int zkPort = config.getObjectList("zookeeper.servers").get(0).getInt("port");

        // sanity check.
        ZookeeperClient.checkConnectionString(zkServer);
        if (numNodes < 1)
            throw new IllegalArgumentException("numNodes < 1");

        this.tmList = new ArrayList<>();

        this.peList = new ArrayList<>();

        // ------- bootstrap zookeeper server -------

        if (startupZookeeper) {
            final File dir = new File(System.getProperty("java.io.tmpdir"), "zookeeper").getAbsoluteFile();

            LOG.info("CREATE TMP DIRECTORY: '" + dir + "'");

            if (dir.exists()) {
                try {
                    FileUtils.deleteDirectory(dir);
                } catch (IOException e) {
                    LOG.error(e.getLocalizedMessage(), e);
                }
            }

            try {
                this.zookeeperServer = new ZooKeeperServer(dir, dir, tickTime);
                this.zookeeperServer.setMaxSessionTimeout(10000000);
                this.zookeeperServer.setMinSessionTimeout(10000000);
                this.zookeeperCNXNFactory = new NIOServerCnxnFactory();
                this.zookeeperCNXNFactory.configure(new InetSocketAddress(zkPort), numConnections);
                this.zookeeperCNXNFactory.startup(zookeeperServer);
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        } else {
            zookeeperServer = null;
            zookeeperCNXNFactory = null;
        }

        // ------- bootstrap local cluster -------

        switch (mode) {

            case EXECUTION_MODE_SINGLE_PROCESS: {
                new WorkloadManager(IConfigFactory.load(IConfig.Type.WM));
                for (int i = 0; i < numNodes; ++i) {
                    tmList.add(new TaskManager(IConfigFactory.load(IConfig.Type.TM)));
                }
            }
                break;

            case EXECUTION_MODE_MULTIPLE_PROCESSES: {
                try {
                    //@formatter:off
                    String[] jvmOpts = config.hasPath("app.profile")
                            ? new String[] { String.format("-Dapp.profile=%s", config.getString("app.profile")) }
                            : new String[] { };
                    //@formatter:on

                    peList.add(new ProcessExecutor(WorkloadManager.class).execute(jvmOpts));
                    for (int i = 0; i < numNodes; ++i) {
                        peList.add(new ProcessExecutor(TaskManager.class).execute(jvmOpts));
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
                break;

            default:
                throw new IllegalStateException("execution mode not known");
        }
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    /**
     *
     */
    public void shutdown() {
        for (final ProcessExecutor pe : peList) {
            pe.destroy();
        }
        this.zookeeperCNXNFactory.closeAll();
        System.exit(0);
    }
}
