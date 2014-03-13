package de.tuberlin.aura.core.task.common;


import de.tuberlin.aura.core.common.eventsystem.Event;
import de.tuberlin.aura.core.common.eventsystem.EventDispatcher;
import de.tuberlin.aura.core.descriptors.Descriptors;
import org.apache.log4j.Logger;

public final class TaskExecutionManager extends EventDispatcher {

    // ---------------------------------------------------
    // Execution Manager Events.
    // ---------------------------------------------------

    public static final class TaskExecutionEvent extends Event {

        public static final String EXECUTION_MANAGER_EVENT_UNREGISTER_TASK = "EXECUTION_MANAGER_EVENT_UNREGISTER_TASK";

        public TaskExecutionEvent(String type, Object payload) {
            super(type, payload);
        }
    }

    // ---------------------------------------------------
    // Fields.
    // ---------------------------------------------------

    private static final Logger LOG = Logger.getLogger(TaskExecutionManager.class);

    private final int numberOfCores;

    private TaskExecutionUnit[] executionUnit;

    // ---------------------------------------------------
    // Constructors.
    // ---------------------------------------------------

    public TaskExecutionManager(final Descriptors.MachineDescriptor machineDescriptor) {
        super(false);

        // sanity check.
        if (machineDescriptor == null)
            throw new IllegalArgumentException("machineDescriptor == null");

        this.numberOfCores = machineDescriptor.hardware.cpuCores;

        this.executionUnit = new TaskExecutionUnit[numberOfCores];

        initializeExecutionUnits();
    }

    // ---------------------------------------------------
    // Public Methods.
    // ---------------------------------------------------

    /**
     * @param driverContext
     */
    public void scheduleTask(final TaskDriverContext driverContext) {
        // sanity check.
        if (driverContext == null)
            throw new IllegalArgumentException("driverContext == null");

        int tmpMin, tmpMinOld;
        tmpMin = tmpMinOld = executionUnit[0].getNumberOfEnqueuedTasks();
        int selectedEU = 0;

        for (int i = 1; i < numberOfCores; ++i) {
            tmpMin = executionUnit[i].getNumberOfEnqueuedTasks();
            if (tmpMin < tmpMinOld) {
                tmpMinOld = tmpMin;
                selectedEU = i;
            }
        }

        executionUnit[selectedEU].enqueueTask(driverContext);

        LOG.info("EXECUTE TASK " + driverContext.taskDescriptor.name + " ["
                + driverContext.taskDescriptor.taskID + "]" + " ON EXECUTION UNIT ("
                + executionUnit[selectedEU].getExecutionUnitID() + ")");
    }


    // ---------------------------------------------------
    // Private Methods.
    // ---------------------------------------------------

    /**
     *
     */
    private void initializeExecutionUnits() {
        for (int i = 0; i < numberOfCores; ++i) {
            this.executionUnit[i] = new TaskExecutionUnit(this, i);
            this.executionUnit[i].start();
        }
    }
}
