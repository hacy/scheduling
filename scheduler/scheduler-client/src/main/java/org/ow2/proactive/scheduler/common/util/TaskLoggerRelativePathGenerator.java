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
package org.ow2.proactive.scheduler.common.util;

import org.ow2.proactive.scheduler.common.task.TaskId;


public class TaskLoggerRelativePathGenerator {

    // the prefix for log file produced in localspace
    private static final String LOG_FILE_PREFIX = "TaskLogs";

    private final String relativePath;

    private final String fileName;

    public TaskLoggerRelativePathGenerator(TaskId taskId) {
        this.fileName = LOG_FILE_PREFIX + "-" + taskId.getJobId() + "-" + taskId.value() + ".log";
        this.relativePath = taskId.getJobId().toString() + "/" + fileName;
    }

    public String getRelativePath() {
        return this.relativePath;
    }

    public String getFileName() {
        return this.fileName;
    }

}
