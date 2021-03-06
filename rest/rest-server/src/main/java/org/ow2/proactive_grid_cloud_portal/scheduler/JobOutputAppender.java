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
package org.ow2.proactive_grid_cloud_portal.scheduler;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;
import org.ow2.proactive.scheduler.common.exception.NotConnectedException;
import org.ow2.proactive.scheduler.common.exception.PermissionException;
import org.ow2.proactive.scheduler.common.exception.UnknownJobException;
import org.ow2.proactive.scheduler.common.task.Log4JTaskLogs;


public class JobOutputAppender extends AppenderSkeleton {

    private JobOutput jobOutput = new JobOutput();

    public JobOutputAppender() throws NotConnectedException, UnknownJobException, PermissionException {
        this.name = "Appender for job output";

        this.setLayout(Log4JTaskLogs.getTaskLogLayout());
    }

    @Override
    protected void append(LoggingEvent event) {
        if (!super.closed) {
            jobOutput.log(this.layout.format(event));
        }
    }

    @Override
    public void close() {
        super.closed = true;
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    public String fetchNewLogs() {
        return jobOutput.fetchNewLogs();
    }

    public String fetchAllLogs() {
        return jobOutput.fetchAllLogs();
    }

    public int size() {
        return jobOutput.size();
    }
}
