/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tajo.engine.planner.physical;

import org.apache.commons.logging.Log;
import org.apache.hadoop.fs.Path;
import org.apache.tajo.SessionVars;
import org.apache.tajo.catalog.Schema;
import org.apache.tajo.catalog.SchemaObject;
import org.apache.tajo.catalog.statistics.TableStats;
import org.apache.tajo.engine.codegen.CompilationError;
import org.apache.tajo.storage.Tuple;
import org.apache.tajo.worker.TaskAttemptContext;

import java.io.IOException;
import java.util.UUID;

public abstract class PhysicalExec implements SchemaObject {
  protected final TaskAttemptContext context;
  protected Schema inSchema;
  protected Schema outSchema;
  protected int outColumnNum;

  public PhysicalExec(final TaskAttemptContext context, final Schema inSchema,
                      final Schema outSchema) {
    this.context = context;
    this.inSchema = inSchema;
    this.outSchema = outSchema;
    this.outColumnNum = outSchema.size();
  }

  public final Schema getSchema() {
    return outSchema;
  }

  public void init() throws IOException {
    if (context.getQueryContext().getBool(SessionVars.CODEGEN)) {
      this.compile();
    }
  }

  protected void compile() throws CompilationError {
  }

  public abstract Tuple next() throws IOException;

  public abstract void rescan() throws IOException;

  public abstract void close() throws IOException;

  public abstract float getProgress();

  protected void info(Log log, String message) {
    log.info("["+ context.getTaskId() + "] " + message);
  }

  protected void debug(Log log, String message) {
    log.debug("["+ context.getTaskId() + "] " + message);
  }

  protected void warn(Log log, String message) {
    log.warn("[" + context.getTaskId() + "] " + message);
  }

  protected void fatal(Log log, String message) {
    log.fatal("[" + context.getTaskId() + "] " + message);
  }

  protected Path getExecutorTmpDir() {
    return new Path(context.getTaskId().getTaskId().getExecutionBlockId().getQueryId().toString(),
        UUID.randomUUID().toString());
  }

  public TableStats getInputStats() {
    return null;
  }
}
