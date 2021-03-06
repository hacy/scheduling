/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package performancetests.metrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.proactive.core.config.ProActiveConfiguration;
import org.ow2.proactive.resourcemanager.RMFactory;
import org.ow2.proactive.scheduler.common.job.JobId;
import org.ow2.proactive.scheduler.common.job.JobState;
import org.ow2.proactive.scheduler.common.job.TaskFlowJob;

import functionaltests.utils.SchedulerTHelper;
import performancetests.recovery.PerformanceTestBase;


/**
 * The performance test calculates average time to scheduler (to dispatch) task, i.e. time to move
 * task from pending till running state.
 * Test repeats same experiment given number of times. In each experiment it submits job with a single task,
 * waits until job is finished, and then computes scheduling time, as difference between timestamp when job was submitted,
 * and timestamp when task was started.
 */
@RunWith(Parameterized.class)
public class TaskSchedulingTimeTest extends PerformanceTestBase {

    /**
     * initialize test with static parameters, where first argument is a number of experiments, and second
     * is a limit which average scheduling time should not cross.
     * @return
     */
    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] { { 1000, 2000 } });
    }

    private final int numberOfExperiments;

    // limit for average scheduling itme
    private final long timeLimit;

    private List<JobId> jobIds = new ArrayList<>();

    public TaskSchedulingTimeTest(int taskNumber, long timeLimit) {
        this.numberOfExperiments = taskNumber;
        this.timeLimit = timeLimit;
    }

    @Test(timeout = 3600000)
    public void test() throws Exception {
        ProActiveConfiguration.load();
        RMFactory.setOsJavaProperty();
        schedulerHelper = new SchedulerTHelper(false,
                                               SCHEDULER_CONFIGURATION_START.getPath(),
                                               RM_CONFIGURATION_START.getPath(),
                                               null);

        schedulerHelper.createNodeSourceWithInfiniteTimeout("local", numberOfExperiments);

        final TaskFlowJob job = SchedulerEfficiencyMetricsTest.createJob(1, 10);

        long totalTime = 0;
        for (int i = 0; i < numberOfExperiments; ++i) {
            JobId jobId = schedulerHelper.submitJob(job);
            jobIds.add(jobId);
            schedulerHelper.waitForEventJobFinished(jobId);
            final JobState jobState = schedulerHelper.getSchedulerInterface().getJobState(jobId);
            final long submittedTime = jobState.getSubmittedTime();
            final long taskStartTime = jobState.getTasks().get(0).getStartTime();
            final long timeToScheduleTask = taskStartTime - submittedTime;
            totalTime += timeToScheduleTask;
        }
        long averageTime = totalTime / numberOfExperiments;
        LOGGER.info(makeCSVString("AverageTaskSchedulingTime",
                                  numberOfExperiments,
                                  timeLimit,
                                  averageTime,
                                  ((averageTime < timeLimit) ? SUCCESS : FAILURE)));
    }

    @After
    public void after() throws Exception {
        if (schedulerHelper != null) {
            for (JobId jobId : jobIds) {
                if (!schedulerHelper.getSchedulerInterface().getJobState(jobId).isFinished()) {
                    schedulerHelper.getSchedulerInterface().killJob(jobId);
                }
                schedulerHelper.getSchedulerInterface().removeJob(jobId);
            }
            schedulerHelper.log("Kill Scheduler after test.");
            schedulerHelper.killScheduler();
        }
    }
}
