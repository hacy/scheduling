/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2015 INRIA/University of
 *                 Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s): ActiveEon Team - http://www.activeeon.com
 *
 * ################################################################
 * $$ACTIVEEON_CONTRIBUTOR$$
 */
package org.ow2.proactive.scheduler.task.internal;

import org.objectweb.proactive.ActiveObjectCreationException;
import org.objectweb.proactive.core.node.Node;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.core.util.converter.ProActiveMakeDeepCopy;
import org.ow2.proactive.scheduler.common.exception.ExecutableCreationException;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.JobInfo;
import org.ow2.proactive.scheduler.common.task.Task;
import org.ow2.proactive.scheduler.common.task.TaskId;
import org.ow2.proactive.scheduler.common.task.TaskInfo;
import org.ow2.proactive.scheduler.common.task.TaskState;
import org.ow2.proactive.scheduler.common.task.TaskStatus;
import org.ow2.proactive.scheduler.common.task.flow.FlowAction;
import org.ow2.proactive.scheduler.common.task.flow.FlowActionType;
import org.ow2.proactive.scheduler.common.task.flow.FlowBlock;
import org.ow2.proactive.scheduler.core.properties.PASchedulerProperties;
import org.ow2.proactive.scheduler.job.InternalJob;
import org.ow2.proactive.scheduler.task.SchedulerVars;
import org.ow2.proactive.scheduler.task.TaskIdImpl;
import org.ow2.proactive.scheduler.task.TaskInfoImpl;
import org.ow2.proactive.scheduler.task.TaskLauncher;
import org.ow2.proactive.scheduler.task.TaskLauncherInitializer;
import org.ow2.proactive.scheduler.task.containers.ExecutableContainer;
import org.ow2.proactive.utils.NodeSet;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Internal and global description of a task.
 * This class contains all information about the task to launch.
 * It also provides methods to create its own launcher and manage the content regarding the scheduling order.
 * Specific internal task may extend this abstract class.
 *
 * @author The ProActive Team
 * @since ProActive Scheduling 0.9
 */
@XmlAccessorType(XmlAccessType.FIELD)
public abstract class InternalTask extends TaskState {

    /** Parents list: null if no dependency */
    @XmlTransient
    private transient List<InternalTask> ideps = null;

    /** Information about the launcher and node, mutable can change overtime, in case of restart for instance */
    // These information are not required during task process
    private transient ExecuterInformation executerInformation;

    /** Task information : this is the information that can change during process. */
    private TaskInfoImpl taskInfo = new TaskInfoImpl();

    /** Node exclusion for this task if desired */
    @XmlTransient
    private transient NodeSet nodeExclusion = null;

    /** Contains the user executable */
    @XmlTransient
    protected transient ExecutableContainer executableContainer = null;

    /** Maximum number of execution for this task in case of failure (node down) */
    private int maxNumberOfExecutionOnFailure = PASchedulerProperties.NUMBER_OF_EXECUTION_ON_FAILURE
            .getValueAsInt();

    /** iteration number if the task was replicated by a IF control flow action */
    private int iteration = 0;

    /** replication number if the task was replicated by a REPLICATE control flow action */
    private int replication = 0;

    /** If this{@link #getFlowBlock()} != {@link FlowBlock#NONE},
     * each start block has a matching end block and vice versa */
    private String matchingBlock = null;

    /** if this task is the JOIN task of a {@link FlowActionType#IF} action,
     * this list contains the 2 end-points (tagged {@link FlowBlock#END}) of the
     * IF and ELSE branches */
    @XmlTransient
    private transient List<InternalTask> joinedBranches = null;

    /** if this task is the IF or ELSE task of a {@link FlowActionType#IF} action,
     * this fields points to the task performing the corresponding IF action */
    @XmlTransient
    private transient InternalTask ifBranch = null;

    private transient InternalTask replicatedFrom;

    void setReplicatedFrom(InternalTask replicatedFrom) {
        this.replicatedFrom = replicatedFrom;
    }

    public InternalTask getReplicatedFrom() {
        return replicatedFrom;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public TaskState replicate() throws ExecutableCreationException {
        /*
         * this implementation deep copies everything using serialization. however the new
         * InternalTask cannot be strictly identical and we have to handle the following special
         * cases:
         *
         * - ExecutableContainer is transient and not copied during serialization. It needs to be
         * manually copied, and added to the InternalTask replica
         *
         * - Using the TaskInfo of _this_ gives us a FINISHED task, need to explicitely create a new
         * clean one.
         *
         * - InternalTask dependencies need to be nulled as they contain references to other
         * InternalTasks, and will be rewritten later anyway
         *
         * - Most of the objects down the object graph contain Hibernate @Id fields. If all those
         * fields are not set to 0 when inserting the object in DB, insertion will fail.
         *
         * - Collections are mapped to specific Hibernate internal collections at runtime, which
         * contain references to the @Id fields mentionned above. They need to be reset too.
         */

        InternalTask replicatedTask = null;
        // SCHEDULING-1373 remains, but not replicating the container make the core hangs,
        // while replicating it "only" loses tasks args in db...
        //ExecutableContainer replicatedContainer = null;
        try {
            // Deep copy of the InternalTask using proactive serialization
            replicatedTask = (InternalTask) ProActiveMakeDeepCopy.WithProActiveObjectStream
                    .makeDeepCopy(this);
        } catch (Throwable t) {
            throw new ExecutableCreationException("Failed to serialize task", t);
        }

        // ideps contain references to other InternalTasks, it needs to be removed.
        // anyway, dependencies for the new task will not be the same as the original
        replicatedTask.ideps = null;

        // the taskinfo needs to be cleaned so that we don't tag this task as finished
        TaskId repId = replicatedTask.taskInfo.getTaskId();
        replicatedTask.taskInfo = new TaskInfoImpl();
        replicatedTask.taskInfo.setTaskId(repId); // we only need this id for the HashSet comparisons...
        replicatedTask.taskInfo.setNumberOfExecutionLeft(getMaxNumberOfExecution());
        replicatedTask.taskInfo.setNumberOfExecutionOnFailureLeft(getMaxNumberOfExecutionOnFailure());

        replicatedTask.setReplicatedFrom(this);

        /*
         * uncomment this to have a close look at the serialized graph you will need to add some
         * jars (http://xstream.codehaus.org/) to the classpath XStream x = new XStream(); String sx
         * = x.toXML(replicatedTask); System.out.println("----------"); System.out.println(sx);
         * System.out.println("----------");
         */

        // We cannot register the newly created InternalTask for DB insertion now,
        // since it only makes sense to hibernate once it's added to the parent InternalJob
        // The next DB.update(InternalJob) will take care of it
        return replicatedTask;
    }

    /**
     * Accumulates in <code>acc</code>  replications of all the tasks that recursively
     * depend on <code>this</code> until <code>target</code> is met
     *
     * @param acc tasks accumulator
     * @param target stopping condition
     * @param loopAction true if the action performed is a LOOP, false is it is a replicate
     * @param dupIndex replication index threshold if <code>ifAction == true</code>
     *                 replication index to set to the old tasks if <code>ifAction == false</code>
     * @param itIndex iteration index threshold it <code>ifAction == true</code>
     *
     * @throws ExecutableCreationException one task could not be replicated
     */
    public void replicateTree(Map<TaskId, InternalTask> acc, TaskId target, boolean loopAction, int dupIndex,
            int itIndex) throws ExecutableCreationException {

        Map<TaskId, InternalTask> tmp = new HashMap<>();

        // replicate the tasks
        internalReplicateTree(tmp, target, loopAction, dupIndex, itIndex);

        // remove replicates from nested LOOP action
        Map<String, Entry<TaskId, InternalTask>> map = new HashMap<>();
        for (Entry<TaskId, InternalTask> it : tmp.entrySet()) {
            String name = it.getValue().getAmbiguousName();
            if (map.containsKey(name)) {
                Entry<TaskId, InternalTask> cur = map.get(name);
                if (it.getValue().getIterationIndex() < cur.getValue().getIterationIndex()) {
                    map.put(name, it);
                }
            } else {
                map.put(name, it);
            }
        }

        for (Entry<TaskId, InternalTask> it : map.values()) {
            acc.put(it.getKey(), it.getValue());
        }

        // reconstruct the dependencies
        internalReconstructTree(acc, target, loopAction, dupIndex, itIndex);
    }

    /**
     * Internal recursive delegate of {@link #replicateTree(Map, TaskId, boolean, int, int)} for task replication
     *
     * @param acc accumulator
     * @param target end condition
     * @param loopAction true if the action performed is a LOOP, false is it is a replicate
     * @param dupIndex replication index threshold if <code>ifAction == true</code>
     *                 replication index to set to the old tasks if <code>ifAction == false</code>
     * @param itIndex iteration index threshold it <code>ifAction == true</code>
     *
     * @throws ExecutableCreationException one task could not be replicated
     */
    private void internalReplicateTree(Map<TaskId, InternalTask> acc, TaskId target, boolean loopAction,
            int dupIndex, int itIndex) throws ExecutableCreationException {

        InternalTask nt = null;
        if (!acc.containsKey(this.getId())) {
            nt = (InternalTask) this.replicate();

            // when nesting REPLICATE actions, the replication index of the original tasks will change
            if (loopAction) {
                nt.setIterationIndex(this.getIterationIndex() + 1);
            }

            acc.put(this.getTaskInfo().getTaskId(), nt);

            // recursive call
            if (!this.getTaskInfo().getTaskId().equals(target)) {
                if (this.getIDependences() != null) {
                    Map<String, InternalTask> deps = new HashMap<>();
                    for (InternalTask parent : this.getIDependences()) {
                        // filter out replicated tasks
                        if (deps.containsKey(parent.getAmbiguousName())) {
                            InternalTask dep = deps.get(parent.getAmbiguousName());
                            if (dep.getReplicationIndex() > parent.getReplicationIndex()) {
                                deps.put(parent.getAmbiguousName(), parent);
                            }
                        } else {
                            deps.put(parent.getAmbiguousName(), parent);
                        }
                    }
                    for (InternalTask parent : deps.values()) {
                        parent.internalReplicateTree(acc, target, loopAction, dupIndex, itIndex);
                    }
                }
                if (this.getJoinedBranches() != null) {
                    for (InternalTask parent : this.getJoinedBranches()) {
                        parent.internalReplicateTree(acc, target, loopAction, dupIndex, itIndex);
                    }
                }
                if (this.getIfBranch() != null) {
                    this.getIfBranch().internalReplicateTree(acc, target, loopAction, dupIndex, itIndex);
                }
            }
        }
    }

    /**
     * Internal recursive delegate of {@link #replicateTree(Map, TaskId, boolean, int, int)} for dependence replication
     *
     * @param acc accumulator
     * @param target end condition
     * @param loopAction true if the action performed is an if, false is it is a replicate
     * @param dupIndex replication index threshold if <code>ifAction == true</code>
     *                 replication index to set to the old tasks if <code>ifAction == false</code>
     * @param itIndex iteration index threshold it <code>ifAction == true</code>
     *
     * @throws Exception instantiation error
     */
    private void internalReconstructTree(Map<TaskId, InternalTask> acc, TaskId target, boolean loopAction,
            int dupIndex, int itIndex) {
        if (target.equals(this.getId())) {
            return;
        }

        InternalTask nt = acc.get(this.getId());

        Map<String, InternalTask> ideps = new HashMap<>();
        int deptype = 0;
        if (this.getIfBranch() != null) {
            deptype = 1;
            ideps.put(this.getIfBranch().getAmbiguousName(), this.getIfBranch());
        } else if (this.getJoinedBranches() != null && this.getJoinedBranches().size() == 2) {
            deptype = 2;
            ideps.put(this.getJoinedBranches().get(0).getAmbiguousName(), this.getJoinedBranches().get(0));
            ideps.put(this.getJoinedBranches().get(1).getAmbiguousName(), this.getJoinedBranches().get(1));
        } else if (this.hasDependences()) {
            // hard dependency check has to be exclusive and AFTER if branch checks :
            // if an FlowAction#IF was executed, we should have both weak and hard dependencies,
            // although only the weak one should be copied on the replicated task
            // ideps.addAll(this.getIDependences());
            for (InternalTask parent : this.getIDependences()) {
                // filter out replicated tasks
                if (ideps.containsKey(parent.getAmbiguousName())) {
                    InternalTask dep = ideps.get(parent.getAmbiguousName());
                    if (dep.getReplicationIndex() > parent.getReplicationIndex()) {
                        ideps.put(parent.getAmbiguousName(), parent);
                    }
                } else {
                    ideps.put(parent.getAmbiguousName(), parent);
                }
            }

        }
        if (ideps.size() == 0) {
            return;
        }

        for (InternalTask parent : ideps.values()) {
            if (acc.get(parent.getId()) == null && nt != null) {
                // tasks are skipped to prevent nested LOOP
                while (acc.get(parent.getId()) == null) {
                    InternalTask np = parent.getIDependences().get(0);
                    if (np == null) {
                        if (parent.getIfBranch() != null) {
                            np = parent.getIfBranch();
                        } else if (parent.getJoinedBranches() != null) {
                            np = parent.getJoinedBranches().get(0);
                        }
                    }
                    parent = np;
                }

                InternalTask tg = acc.get(parent.getId());
                nt.addDependence(tg);
            } else if (nt != null) {
                InternalTask tg = acc.get(parent.getId());
                boolean hasit = false;
                switch (deptype) {
                    case 0:
                        if (nt.hasDependences()) {
                            for (InternalTask it : nt.getIDependences()) {
                                // do NOT use contains(): relies on getId() which is null
                                if (it.getName().equals(tg.getName())) {
                                    hasit = true;
                                }
                            }
                        }
                        if (!hasit) {
                            nt.addDependence(tg);
                        }
                        break;
                    case 1:
                        nt.setIfBranch(tg);
                        break;
                    case 2:
                        if (nt.getJoinedBranches() == null) {
                            List<InternalTask> jb = new ArrayList<>();
                            nt.setJoinedBranches(jb);
                        }
                        for (InternalTask it : nt.getJoinedBranches()) {
                            if (it.getName().equals(tg.getName())) {
                                hasit = true;
                            }
                        }
                        if (!hasit) {
                            nt.getJoinedBranches().add(tg);
                        }
                        break;
                }

            }

            if (!parent.getTaskInfo().getTaskId().equals(target)) {
                parent.internalReconstructTree(acc, target, loopAction, dupIndex, itIndex);
            }
        }
    }

    /**
     * Recursively checks if this is a dependence,
     * direct or indirect, of <code>parent</code>
     * <p>
     * Direct dependence means through {@link Task#getDependencesList()},
     * indirect dependence means through weak dependences induced by
     * {@link FlowActionType#IF}, materialized by
     * {@link InternalTask#getIfBranch()} and {@link InternalTask#getJoinedBranches()}.
     *
     * @param parent the dependence to find
     * @return true if this depends on <code>parent</code>
     */
    public boolean dependsOn(InternalTask parent) {
        return internalDependsOn(parent, 0);
    }

    private boolean internalDependsOn(InternalTask parent, int depth) {
        if (this.getId().equals(parent.getId())) {
            return (depth >= 0);
        }
        if (this.getIDependences() == null && this.getJoinedBranches() == null && this.getIfBranch() == null) {
            return false;
        }
        if (this.joinedBranches != null) {
            for (InternalTask it : this.getJoinedBranches()) {
                if (!it.internalDependsOn(parent, depth - 1)) {
                    return false;
                }
            }
        } else if (this.getIfBranch() != null) {
            if (!this.getIfBranch().internalDependsOn(parent, depth + 1)) {
                return false;
            }
        } else if (this.getIDependences() != null) {
            for (InternalTask it : this.getIDependences()) {
                if (!it.internalDependsOn(parent, depth)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void setExecutableContainer(ExecutableContainer e) {
        this.executableContainer = e;
    }

    /**
     * Create the launcher for this taskDescriptor.
     *
     * @param node the node on which to create the launcher.
     * @param job the job on which to create the launcher.
     * @return the created launcher as an activeObject.
     */
    public abstract TaskLauncher createLauncher(InternalJob job, Node node)
            throws ActiveObjectCreationException, NodeException;

    /**
     * Return true if this task can handle parent results arguments in its executable
     *
     * @return true if this task can handle parent results arguments in its executable, false if not.
     */
    public abstract boolean handleResultsArguments();

    /**
     * Return a container for the user executable represented by this task descriptor.
     *
     * @return the user executable represented by this task descriptor.
     */
    public ExecutableContainer getExecutableContainer() {
        return this.executableContainer;
    }

    /**
     * Add a dependence to the list of dependences for this taskDescriptor.
     * The tasks in this list represents the tasks that the current task have to wait for before starting.
     *
     * @param task a super task of this task.
     */
    public void addDependence(InternalTask task) {
        if (ideps == null) {
            ideps = new ArrayList<>();
        }
        ideps.add(task);
    }

    public boolean removeDependence(InternalTask task) {
        if (ideps != null) {
            return ideps.remove(task);
        }
        return false;
    }

    /**
     * Return true if this task has dependencies.
     * It means the first eligible tasks in case of TASK_FLOW job type.
     *
     * @return true if this task has dependencies, false otherwise.
     */
    public boolean hasDependences() {
        return (ideps != null && ideps.size() > 0);
    }

    /**
     * @see org.ow2.proactive.scheduler.common.task.TaskState#getTaskInfo()
     */
    @Override
    public TaskInfo getTaskInfo() {
        return taskInfo;
    }

    /**
     * @see org.ow2.proactive.scheduler.common.task.TaskState#update(org.ow2.proactive.scheduler.common.task.TaskInfo)
     */
    @Override
    public synchronized void update(TaskInfo taskInfo) {
        if (!getId().equals(taskInfo.getTaskId())) {
            throw new IllegalArgumentException(
                "This task info is not applicable for this task. (expected id is '" + getId() +
                    "' but was '" + taskInfo.getTaskId() + "'");
        }
        this.taskInfo = (TaskInfoImpl) taskInfo;
    }


    @Override
    public void setMaxNumberOfExecution(int numberOfExecution) {
        super.setMaxNumberOfExecution(numberOfExecution);
        this.taskInfo.setNumberOfExecutionLeft(numberOfExecution);
        this.taskInfo.setNumberOfExecutionOnFailureLeft(maxNumberOfExecutionOnFailure);
    }

    public void setNumberOfExecutionLeft(int numberOfExecutionLeft) {
        this.taskInfo.setNumberOfExecutionLeft(numberOfExecutionLeft);
    }

    public void setNumberOfExecutionOnFailureLeft( int numberOfExecutionOnFailureLeft) {
        this.taskInfo.setNumberOfExecutionOnFailureLeft(numberOfExecutionOnFailureLeft);
    }

    /**
     * To set the finishedTime
     *
     * @param finishedTime the finishedTime to set
     */
    public void setFinishedTime(long finishedTime) {
        taskInfo.setFinishedTime(finishedTime);
        //set max progress if task is finished
        if (finishedTime > 0) {
            taskInfo.setProgress(100);
        }
    }

    /**
     * To set the jobId
     *
     * @param id the jobId to set
     */
    public void setJobId(JobId id) {
        taskInfo.setJobId(id);
    }

    /**
     * To set the startTime
     *
     * @param startTime the startTime to set
     */
    public void setStartTime(long startTime) {
        taskInfo.setStartTime(startTime);
    }

    public void setInErrorTime(long inErrorTime) {
        taskInfo.setInErrorTime(inErrorTime);
    }

    /**
     * To set the scheduledTime
     *
     * @param scheduledTime the scheduledTime to set
     */
    public void setScheduledTime(long scheduledTime) {
        taskInfo.setScheduledTime(scheduledTime);
    }

    /**
     * To set the taskId
     *
     * @param taskId the taskId to set
     */
    public void setId(TaskId taskId) {
        taskInfo.setTaskId(taskId);
    }

    /**
     * Set the job info to this task.
     *
     * @param jobInfo a job info containing job id and others informations
     */
    public void setJobInfo(JobInfo jobInfo) {
        taskInfo.setJobInfo(jobInfo);
    }

    /**
     * To set the status
     *
     * @param taskStatus the status to set
     */
    public void setStatus(TaskStatus taskStatus) {
        taskInfo.setStatus(taskStatus);
    }

    /**
     * Set the real execution duration for the task.
     *
     * @param duration the real duration of the execution of the task
     */
    public void setExecutionDuration(long duration) {
        taskInfo.setExecutionDuration(duration);
    }

    /**
     * To get the dependences of this task as internal tasks.
     * Return null if this task has no dependence.
     *
     * @return the dependences
     */
    public List<InternalTask> getIDependences() {
        //set to null if needed
        if (ideps != null && ideps.size() == 0) {
            ideps = null;
        }
        return ideps;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @XmlTransient
    public List<TaskState> getDependences() {
        //set to null if needed
        if (ideps == null || ideps.size() == 0) {
            ideps = null;
            return null;
        }
        List<TaskState> tmp = new ArrayList<>(ideps.size());
        for (TaskState ts : ideps) {
            tmp.add(ts);
        }
        return tmp;
    }

    /**
     * To set the executionHostName
     *
     * @param executionHostName the executionHostName to set
     */
    public void setExecutionHostName(String executionHostName) {
        taskInfo.setExecutionHostName(executionHostName);
    }

    /**
     * To get the executer informations
     *
     * @return the executerInformation
     */
    public ExecuterInformation getExecuterInformation() {
        return executerInformation;
    }

    /**
     * To set the executer informations.
     *
     * @param executerInformation the executerInformation to set
     */
    public void setExecuterInformation(ExecuterInformation executerInformation) {
        this.executerInformation = executerInformation;
    }

    /**
     * Returns the node Exclusion group.
     *
     * @return the node Exclusion group.
     */
    public NodeSet getNodeExclusion() {
        return nodeExclusion;
    }

    /**
     * Sets the nodes Exclusion to the given nodeExclusion value.
     *
     * @param nodeExclusion the nodeExclusion to set.
     */
    public void setNodeExclusion(NodeSet nodeExclusion) {
        this.nodeExclusion = nodeExclusion;
    }

    /**
     * Decrease the number of re-run left.
     */
    public void decreaseNumberOfExecutionLeft() {
        taskInfo.decreaseNumberOfExecutionLeft();
    }

    /**
     * Decrease the number of execution on failure left.
     */
    public void decreaseNumberOfExecutionOnFailureLeft() {
        taskInfo.decreaseNumberOfExecutionOnFailureLeft();
    }

    /**
     * @see org.ow2.proactive.scheduler.common.task.TaskState#getMaxNumberOfExecutionOnFailure()
     */
    @Override
    public int getMaxNumberOfExecutionOnFailure() {
        return maxNumberOfExecutionOnFailure;
    }

    /**
     * Set the current progress of the task
     *
     * @param progress the current progress of the task
     */
    public void setProgress(Integer progress) {
        taskInfo.setProgress(progress);
    }

    /**
     * To set the name of this task.
     * <p>
     * The provided String will be appended the iteration and replication index
     * if &gt; 0, so that
     * <code>name = name [ITERATION_SEPARATOR iteration] [REPLICATION_SEPARATOR replication]</code>.
     *
     * @param newName
     *            the name to set.
     */
    public void setName(String newName) {
        if (newName == null) {
            return;
        }
        int i = -1;
        if ((i = newName.indexOf(TaskId.ITERATION_SEPARATOR)) != -1) {
            newName = newName.substring(0, i);
        } else if ((i = newName.indexOf(TaskId.REPLICATION_SEPARATOR)) != -1) {
            newName = newName.substring(0, i);
        }

        String n = this.getTaskNameSuffix();
        if (newName.length() + n.length() > 255) {
            throw new IllegalArgumentException("The name is too long, it must have 255 chars length max : " +
                newName + n);
        }

        super.setName(newName + n);
        if (this.getId() != null) {
            ((TaskIdImpl) this.getId()).setReadableName(newName + n);
        }

        // update matching block name if it exists
        // start/end block tasks should always have the same iteration & replication
        if (this.matchingBlock != null && this.matchingBlock.length() > 0) {
            String m = getInitialName(this.matchingBlock);
            this.setMatchingBlock(m + getTaskNameSuffix());
        }
        // update target name if this task performs a LOOP action
        // same as the matching block name : indexes should match as LOOP initiator and
        // target share the same block scope
        if (this.getFlowScript() != null &&
            this.getFlowScript().getActionType().equals(FlowActionType.LOOP.toString())) {
            String t = getInitialName(this.getFlowScript().getActionTarget());
            this.getFlowScript().setActionTarget(t + getTaskNameSuffix());
        }

        // same stuff with IF
        if (this.getFlowScript() != null &&
            this.getFlowScript().getActionType().equals(FlowActionType.IF.toString())) {
            String ifBranch = getInitialName(this.getFlowScript().getActionTarget());
            String elseBranch = getInitialName(this.getFlowScript().getActionTargetElse());
            this.getFlowScript().setActionTarget(ifBranch + getTaskNameSuffix());
            this.getFlowScript().setActionTargetElse(elseBranch + getTaskNameSuffix());

            if (this.getFlowScript().getActionContinuation() != null) {
                String join = getInitialName(this.getFlowScript().getActionContinuation());
                this.getFlowScript().setActionContinuation(join + getTaskNameSuffix());
            }
        }
    }

    /**
     * Constructs the suffix to append to a task name so that is
     * can be unique among replicated tasks in complex taskflows with loops/replications
     *
     * @return the String suffix to append to a replicated task so that
     * it can be distinguished from the original
     */
    private String getTaskNameSuffix() {
        String n = "";
        if (this.iteration > 0) {
            n += TaskId.ITERATION_SEPARATOR + this.iteration;
        }
        if (this.replication > 0) {
            n += TaskId.REPLICATION_SEPARATOR + this.replication;
        }
        return n;
    }

    /**
     * Extracts the original task name if it was added a suffix to
     * make it unique among replicated tasks
     * <p>
     * <b>This methods returns an ambiguous name: several tasks can share this name;
     * it cannot be used as an identifier.
     * </b>
     * @return the original task name, without the added suffixes for iteration and replication
     */
    private String getAmbiguousName() {
        return getInitialName(this.getName());
    }

    private static final Pattern[] namePatterns;

    static {
        String[] regexps = new String[] { "^(.*)[" + TaskId.ITERATION_SEPARATOR + "].*$",
                "^(.*)[" + TaskId.REPLICATION_SEPARATOR + "].*$", "^(.*)$" };

        namePatterns = new Pattern[regexps.length];

        for (int i = 0; i < regexps.length; i++) {
            namePatterns[i] = Pattern.compile(regexps[i]);
        }
    }

    /**
     * Extracts the original task name if it was added a suffix to
     * make it unique among replicated tasks
     * <p>
     * <b>This methods returns an ambiguous name: several tasks can share this name;
     * it cannot be used as an identifier.
     * </b>
     * @param fullTaskName name with the iteration and replication suffixes
     * @return the original task name, without the added suffixes for iteration and replication
     */
    public static String getInitialName(String fullTaskName) {
        String taskName = null;

        Matcher matcher = null;
        for (Pattern pat : namePatterns) {
            matcher = pat.matcher(fullTaskName);
            if (matcher.find()) {
                taskName = matcher.group(1);
                return taskName;
            }
        }
        throw new RuntimeException("Could not extract task name: " + fullTaskName);
    }

    /**
     * Extracts the replication index from a non ambiguous name:
     * <p>ie: getReplicationIndexFromName("task1*3") returns 3.
     *
     * @param name non ambiguous task name
     * @return the replication index contained in the name
     */
    public static int getReplicationIndexFromName(String name) {
        if (name.indexOf(TaskId.REPLICATION_SEPARATOR) == -1) {
            return 0;
        } else {
            return Integer.parseInt(name.split("[" + TaskId.REPLICATION_SEPARATOR + "]")[1]);
        }
    }

    /**
     * Extracts the iteration index from a non ambiguous name:
     * <p>ie: getIterationIndexFromName("task1#3") returns 3.
     *
     * @param name non ambiguous task name
     * @return the replication index contained in the name
     */
    public static int getIterationIndexFromName(String name) {
        if (name.indexOf(TaskId.ITERATION_SEPARATOR) == -1) {
            return 0;
        } else {
            String suffix = name.split("[" + TaskId.ITERATION_SEPARATOR + "]")[1];
            return Integer.parseInt(suffix.split("[" + TaskId.REPLICATION_SEPARATOR + "]")[0]);
        }
    }

    /**
     * Set the iteration number of this task if it was replicated by a IF flow operations
     * <p>
     * Updates the Task's name consequently, see {@link Task#setName(String)}
     *
     * @param it iteration number, must be {@code >= 0}.
     */
    public void setIterationIndex(int it) {
        if (it < 0) {
            throw new IllegalArgumentException("Cannot set negative iteration index: " + it);
        }
        String taskName = getInitialName(this.getName());
        this.iteration = it;
        this.setName(taskName);
    }

    /**
     * @return the iteration number of this task if it was replicated by a LOOP flow operations ({@code >= 0})
     */
    @Override
    public int getIterationIndex() {
        return this.iteration;
    }

    /**
     * Set the replication number of this task if it was replicated by a REPLICATE flow operations
     *
     * @param it iteration number, must be {@code >= 0}
     */
    public void setReplicationIndex(int it) {
        if (it < 0) {
            throw new IllegalArgumentException("Cannot set negative replication index: " + it);
        }
        String taskName = getInitialName(this.getName());
        this.replication = it;
        this.setName(taskName);
    }

    /**
     * @return the replication number of this task if it was replicated by a REPLICATE flow operations ({@code >= 0})
     */
    @Override
    public int getReplicationIndex() {
        return this.replication;
    }

    /**
     * Control Flow Blocks are formed with pairs of {@link FlowBlock#START} and {@link FlowBlock#END}
     * on tasks. The Matching Block of a Task represents the corresponding
     * {@link FlowBlock#START} of a Task tagged {@link FlowBlock#END}, and vice-versa.
     *
     * @return the name of the Task matching the block started or ended in this task, or null
     */
    public String getMatchingBlock() {
        return this.matchingBlock;
    }

    /**
     * Control Flow Blocks are formed with pairs of {@link FlowBlock#START} and {@link FlowBlock#END}
     * on tasks. The Matching Block of a Task represents the corresponding
     * {@link FlowBlock#START} of a Task tagged {@link FlowBlock#END}, and vice-versa.
     *
     * @param s the name of the Task matching the block started or ended in this task
     */
    public void setMatchingBlock(String s) {
        this.matchingBlock = s;
    }

    /**
     * If a {@link FlowActionType#IF} {@link FlowAction} is performed in a TaskFlow,
     * there exist no hard dependency between the initiator of the action and the IF or ELSE
     * branch. Similarly, there exist no dependency between the IF or ELSE branches
     * and the JOIN task.
     * This method provides an easy way to check if this task joins an IF/ELSE action
     *
     * @return a List of String containing the end-points of the IF and ELSE branches
     *         joined by this task, or null if it does not merge anything.
     */
    public List<InternalTask> getJoinedBranches() {
        return this.joinedBranches;
    }

    /**
     * If a {@link FlowActionType#IF} {@link FlowAction} is performed in a TaskFlow,
     * there exist no hard dependency between the initiator of the action and the IF or ELSE
     * branch. Similarly, there exist no dependency between the IF or ELSE branches
     * and the JOIN task.
     *
     * @param branches sets the List of String containing the end-points
     *        of the IF and ELSE branches joined by this task
     */
    public void setJoinedBranches(List<InternalTask> branches) {
        this.joinedBranches = branches;
    }

    /**
     * If a {@link FlowActionType#IF} {@link FlowAction} is performed in a TaskFlow,
     * there exist no hard dependency between the initiator of the action and the IF or ELSE
     * branch. Similarly, there exist no dependency between the IF or ELSE branches
     * and the JOIN task.
     * This method provides an easy way to check if this task is an IF/ELSE branch.
     *
     * @return the name of the initiator of the IF action that this task is a branch of
     */
    public InternalTask getIfBranch() {
        return this.ifBranch;
    }

    /**
     * If a {@link FlowActionType#IF} {@link FlowAction} is performed in a TaskFlow,
     * there exist no hard dependency between the initiator of the action and the IF or ELSE
     * branch. Similarly, there exist no dependency between the IF or ELSE branches
     * and the JOIN task.
     */
    public void setIfBranch(InternalTask branch) {
        this.ifBranch = branch;
    }

    /**
     * Prepare and return the default task launcher initializer (ie the one that works for every launcher)<br>
     * Concrete launcher may have to add values to the created initializer to bring more information to the launcher.
     *
     * @return the default created task launcher initializer
     */
    protected TaskLauncherInitializer getDefaultTaskLauncherInitializer(InternalJob job) {
        TaskLauncherInitializer tli = new TaskLauncherInitializer();
        tli.setTaskId(getId());
        tli.setJobOwner(job.getJobInfo().getJobOwner());
        tli.setPreScript(getPreScript());
        tli.setPostScript(getPostScript());
        tli.setControlFlowScript(getFlowScript());
        tli.setTaskInputFiles(getInputFilesList());
        tli.setTaskOutputFiles(getOutputFilesList());
        tli.setNamingService(
                job.getTaskDataSpaceApplications().get(getId().longValue()).getNamingServiceStub());
        tli.setIterationIndex(getIterationIndex());
        tli.setReplicationIndex(getReplicationIndex());

        Map<String, String> gInfo = getGenericInformationOverridden(job);
        tli.setGenericInformation(gInfo);

        tli.setForkEnvironment(getForkEnvironment());
        if (isWallTimeSet()) {
            tli.setWalltime(wallTime);
        }
        tli.setPreciousLogs(isPreciousLogs());
        tli.setVariables(job.getVariables());

        tli.setPingPeriod(PASchedulerProperties.SCHEDULER_NODE_PING_FREQUENCY.getValueAsInt());
        tli.setPingAttempts(PASchedulerProperties.SCHEDULER_NODE_PING_ATTEMPTS.getValueAsInt());

        return tli;
    }

    /**
     * @return the generic information of the job overridden eventually by the task's generic info
     */
    public Map<String, String> getGenericInformationOverridden(InternalJob job) {
        HashMap<String, String> gInfo = new HashMap<>();
        gInfo.putAll(job.getGenericInformation());
        gInfo.putAll(getGenericInformation());
        return gInfo;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (InternalTask.class.isAssignableFrom(obj.getClass())) {
            return ((InternalTask) obj).getId().equals(getId());
        }

        return false;
    }

    /**
     *
     * Return generic info replacing $PA_JOB_NAME, $PA_JOB_ID, $PA_TASK_NAME, $PA_TASK_ID, $PA_TASK_ITERATION
     * $PA_TASK_REPLICATION by it's actual value
     *
     */
    public Map<String, String> getGenericInformation() {

        if (taskInfo == null || genericInformations == null) {
            // task is not yet properly initialized
            return new HashMap<>();
        }

        Map<String, String> replacements = new HashMap<>();
        JobId jobId = taskInfo.getJobId();
        if (jobId != null) {
            replacements.put(SchedulerVars.PA_JOB_ID.toString(), jobId.toString());
            replacements.put(SchedulerVars.PA_JOB_NAME.toString(), jobId.getReadableName());
        }
        TaskId taskId = taskInfo.getTaskId();
        if (taskId != null) {
            replacements.put(SchedulerVars.PA_TASK_ID.toString(), taskId.toString());
            replacements.put(SchedulerVars.PA_TASK_NAME.toString(), taskId.getReadableName());
        }
        replacements.put(SchedulerVars.PA_TASK_ITERATION.toString(), String.valueOf(iteration));
        replacements.put(SchedulerVars.PA_TASK_REPLICATION.toString(), String.valueOf(replication));

        return applyReplacementsOnGenericInformation(replacements);
    }

    /**
     *
     * Gets the task generic information.
     * @param replaceVariables - if set to true method replaces variables in the generic information
     *
     */
    public Map<String, String> getGenericInformations(boolean replaceVariables) {
        if (replaceVariables) {
            return this.getGenericInformation();
        } else {
            return super.getGenericInformation();
        }
    }
}
