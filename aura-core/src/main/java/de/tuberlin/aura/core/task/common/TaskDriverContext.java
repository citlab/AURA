package de.tuberlin.aura.core.task.common;

import de.tuberlin.aura.core.common.eventsystem.IEventDispatcher;
import de.tuberlin.aura.core.descriptors.Descriptors;
import de.tuberlin.aura.core.iosystem.IOEvents;
import de.tuberlin.aura.core.iosystem.QueueManager;

/**
 *
 */
public final class TaskDriverContext {

    public final TaskDriverLifecycle taskDriver;

    public final TaskManagerContext managerContext;

    public final Descriptors.TaskDescriptor taskDescriptor;

    public final Descriptors.TaskBindingDescriptor taskBindingDescriptor;

    public final IEventDispatcher driverDispatcher;

    public final QueueManager<IOEvents.DataIOEvent> queueManager;

    public TaskDriverContext(final TaskDriverLifecycle taskDriver,
                             final TaskManagerContext managerContext,
                             final Descriptors.TaskDescriptor taskDescriptor,
                             final Descriptors.TaskBindingDescriptor taskBindingDescriptor,
                             final IEventDispatcher driverDispatcher,
                             final QueueManager<IOEvents.DataIOEvent> queueManager) {

        this.taskDriver = taskDriver;

        this.managerContext = managerContext;

        this.taskDescriptor = taskDescriptor;

        this.taskBindingDescriptor = taskBindingDescriptor;

        this.driverDispatcher = driverDispatcher;

        this.queueManager = queueManager;
    }
}
