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

package org.apache.tajo.engine.planner;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.tajo.LocalTajoTestingUtility;
import org.apache.tajo.OverridableConf;
import org.apache.tajo.QueryTestCaseBase;
import org.apache.tajo.algebra.Expr;
import org.apache.tajo.catalog.CatalogUtil;
import org.apache.tajo.catalog.Schema;
import org.apache.tajo.catalog.TableDesc;
import org.apache.tajo.catalog.TableMeta;
import org.apache.tajo.catalog.partition.PartitionMethodDesc;
import org.apache.tajo.catalog.proto.CatalogProtos;
import org.apache.tajo.common.TajoDataTypes;
import org.apache.tajo.conf.TajoConf;
import org.apache.tajo.engine.query.QueryContext;
import org.apache.tajo.plan.LogicalPlan;
import org.apache.tajo.plan.logical.*;
import org.apache.tajo.plan.rewrite.rules.PartitionedTableRewriter;
import org.apache.tajo.util.CommonTestingUtil;
import org.apache.tajo.util.FileUtil;
import org.apache.tajo.util.KeyValueSet;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static org.junit.Assert.*;

public class TestPartitionedTableRewriter extends QueryTestCaseBase {

  final static String PARTITION_TABLE_NAME = "tb_partition";
  final static String MULTIPLE_PARTITION_TABLE_NAME = "tb_multiple_partition";

  // for getting a method name
  @Rule
  public TestName name = new TestName();

  @BeforeClass
  public static void setUp() throws Exception {
    FileSystem fs = FileSystem.get(conf);
    Path rootDir = TajoConf.getWarehouseDir(testingCluster.getConfiguration());

    Schema schema = new Schema();
    schema.addColumn("n_nationkey", TajoDataTypes.Type.INT8);
    schema.addColumn("n_name", TajoDataTypes.Type.TEXT);
    schema.addColumn("n_regionkey", TajoDataTypes.Type.INT8);

    TableMeta meta = CatalogUtil.newTableMeta("TEXT", new KeyValueSet());

    createExternalTableIncludedOnePartitionKeyColumn(fs, rootDir, schema, meta);
    createExternalTableIncludedMultiplePartitionKeyColumns(fs, rootDir, schema, meta);
  }

  private static void createExternalTableIncludedOnePartitionKeyColumn(FileSystem fs, Path rootDir, Schema schema,
    TableMeta meta) throws Exception {
    Schema partSchema = new Schema();
    partSchema.addColumn("key", TajoDataTypes.Type.TEXT);

    PartitionMethodDesc partitionMethodDesc =
      new PartitionMethodDesc("TestPartitionedTableRewriter", PARTITION_TABLE_NAME,
        CatalogProtos.PartitionType.COLUMN, "key", partSchema);

    Path tablePath = new Path(rootDir, PARTITION_TABLE_NAME);
    fs.mkdirs(tablePath);

    client.createExternalTable(PARTITION_TABLE_NAME, schema, tablePath.toUri(), meta, partitionMethodDesc);

    TableDesc tableDesc = client.getTableDesc(PARTITION_TABLE_NAME);
    assertNotNull(tableDesc);

    Path path = new Path(tableDesc.getUri().toString() + "/key=part123");
    fs.mkdirs(path);
    FileUtil.writeTextToFile("1|ARGENTINA|1", new Path(path, "data"));

    path = new Path(tableDesc.getUri().toString() + "/key=part456");
    fs.mkdirs(path);
    FileUtil.writeTextToFile("2|BRAZIL|1", new Path(path, "data"));

    path = new Path(tableDesc.getUri().toString() + "/key=part789");
    fs.mkdirs(path);
    FileUtil.writeTextToFile("3|CANADA|1", new Path(path, "data"));
  }

  private static void createExternalTableIncludedMultiplePartitionKeyColumns(FileSystem fs, Path rootDir,
      Schema schema, TableMeta meta) throws Exception {
    Schema partSchema = new Schema();
    partSchema.addColumn("key1", TajoDataTypes.Type.TEXT);
    partSchema.addColumn("key2", TajoDataTypes.Type.TEXT);
    partSchema.addColumn("key3", TajoDataTypes.Type.INT8);

    PartitionMethodDesc partitionMethodDesc =
      new PartitionMethodDesc("TestPartitionedTableRewriter", MULTIPLE_PARTITION_TABLE_NAME,
        CatalogProtos.PartitionType.COLUMN, "key1,key2,key3", partSchema);

    Path tablePath = new Path(rootDir, MULTIPLE_PARTITION_TABLE_NAME);
    fs.mkdirs(tablePath);

    client.createExternalTable(MULTIPLE_PARTITION_TABLE_NAME, schema, tablePath.toUri(), meta, partitionMethodDesc);

    TableDesc tableDesc = client.getTableDesc(MULTIPLE_PARTITION_TABLE_NAME);
    assertNotNull(tableDesc);

    Path path = new Path(tableDesc.getUri().toString() + "/key1=part123");
    fs.mkdirs(path);
    path = new Path(tableDesc.getUri().toString() + "/key1=part123/key2=supp123");
    fs.mkdirs(path);
    path = new Path(tableDesc.getUri().toString() + "/key1=part123/key2=supp123/key3=1");
    fs.mkdirs(path);
    FileUtil.writeTextToFile("1|ARGENTINA|1", new Path(path, "data"));

    path = new Path(tableDesc.getUri().toString() + "/key1=part123/key2=supp123/key3=2");
    fs.mkdirs(path);
    FileUtil.writeTextToFile("2|BRAZIL|1", new Path(path, "data"));

    path = new Path(tableDesc.getUri().toString() + "/key1=part789");
    fs.mkdirs(path);
    path = new Path(tableDesc.getUri().toString() + "/key1=part789/key2=supp789");
    fs.mkdirs(path);
    path = new Path(tableDesc.getUri().toString() + "/key1=part789/key2=supp789/key3=3");
    fs.mkdirs(path);
    FileUtil.writeTextToFile("3|CANADA|1", new Path(path, "data"));
  }

  @AfterClass
  public static void tearDown() throws Exception {
    client.executeQuery("DROP TABLE IF EXISTS " + PARTITION_TABLE_NAME + " PURGE;");
    client.executeQuery("DROP TABLE IF EXISTS " + MULTIPLE_PARTITION_TABLE_NAME + " PURGE;");
  }

  @Test
  public void testFilterIncludePartitionKeyColumn() throws Exception {
    Expr expr = sqlParser.parse("SELECT * FROM " + PARTITION_TABLE_NAME + " WHERE key = 'part456' ORDER BY key");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SORT, projNode.getChild().getType());
    SortNode sortNode = projNode.getChild();

    assertEquals(NodeType.SELECTION, sortNode.getChild().getType());
    SelectionNode selNode = sortNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(1, filteredPaths.length);
    assertEquals("key=part456", filteredPaths[0].getName());
  }

  @Test
  public void testWithoutAnyFilters() throws Exception {
    Expr expr = sqlParser.parse("SELECT * FROM " + PARTITION_TABLE_NAME + " ORDER BY key");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SORT, projNode.getChild().getType());
    SortNode sortNode = projNode.getChild();

    assertEquals(NodeType.SCAN, sortNode.getChild().getType());
    ScanNode scanNode = sortNode.getChild();

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(3, filteredPaths.length);
    assertEquals("key=part123", filteredPaths[0].getName());
    assertEquals("key=part456", filteredPaths[1].getName());
    assertEquals("key=part789", filteredPaths[2].getName());
  }

  @Test
  public void testFilterIncludeNonExistingPartitionValue() throws Exception {
    Expr expr = sqlParser.parse("SELECT * FROM " + PARTITION_TABLE_NAME + " WHERE key = 'part123456789'");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SELECTION, projNode.getChild().getType());
    SelectionNode selNode = projNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(0, filteredPaths.length);
  }

  @Test
  public void testFilterIncludeNonPartitionKeyColumn() throws Exception {
    String sql = "SELECT * FROM " + PARTITION_TABLE_NAME + " WHERE n_nationkey = 1";
    Expr expr = sqlParser.parse(sql);
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SELECTION, projNode.getChild().getType());
    SelectionNode selNode = projNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(3, filteredPaths.length);
    assertEquals("key=part123", filteredPaths[0].getName());
    assertEquals("key=part456", filteredPaths[1].getName());
    assertEquals("key=part789", filteredPaths[2].getName());
  }

  @Test
  public void testFilterIncludeEveryPartitionKeyColumn() throws Exception {
    Expr expr = sqlParser.parse("SELECT * FROM " + MULTIPLE_PARTITION_TABLE_NAME
      + " WHERE key1 = 'part789' and key2 = 'supp789' and key3=3");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SELECTION, projNode.getChild().getType());
    SelectionNode selNode = projNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(1, filteredPaths.length);
    assertEquals("key3=3", filteredPaths[0].getName());
    assertEquals("key2=supp789", filteredPaths[0].getParent().getName());
    assertEquals("key1=part789", filteredPaths[0].getParent().getParent().getName());
  }

  @Test
  public void testFilterIncludeSomeOfPartitionKeyColumns() throws Exception {
    Expr expr = sqlParser.parse("SELECT * FROM " + MULTIPLE_PARTITION_TABLE_NAME
      + " WHERE key1 = 'part123' and key2 = 'supp123' order by n_nationkey");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SORT, projNode.getChild().getType());
    SortNode sortNode = projNode.getChild();

    assertEquals(NodeType.SELECTION, sortNode.getChild().getType());
    SelectionNode selNode = sortNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(2, filteredPaths.length);

    assertEquals("key3=1", filteredPaths[0].getName());
    assertEquals("key2=supp123", filteredPaths[0].getParent().getName());
    assertEquals("key1=part123", filteredPaths[0].getParent().getParent().getName());

    assertEquals("key3=2", filteredPaths[1].getName());
    assertEquals("key2=supp123", filteredPaths[1].getParent().getName());
    assertEquals("key1=part123", filteredPaths[1].getParent().getParent().getName());
  }

  @Test
  public void testFilterIncludeNonPartitionKeyColumns() throws Exception {
    Expr expr = sqlParser.parse("SELECT * FROM " + MULTIPLE_PARTITION_TABLE_NAME
      + " WHERE key1 = 'part123' and n_nationkey >= 2 order by n_nationkey");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SORT, projNode.getChild().getType());
    SortNode sortNode = projNode.getChild();

    assertEquals(NodeType.SELECTION, sortNode.getChild().getType());
    SelectionNode selNode = sortNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(2, filteredPaths.length);

    assertEquals("key3=1", filteredPaths[0].getName());
    assertEquals("key2=supp123", filteredPaths[0].getParent().getName());
    assertEquals("key1=part123", filteredPaths[0].getParent().getParent().getName());

    assertEquals("key3=2", filteredPaths[1].getName());
    assertEquals("key2=supp123", filteredPaths[1].getParent().getName());
    assertEquals("key1=part123", filteredPaths[1].getParent().getParent().getName());
  }

  @Test
  public final void testPartitionPruningWitCTAS() throws Exception {
    String tableName = name.getMethodName().toLowerCase();
    String canonicalTableName = CatalogUtil.getCanonicalTableName("\"TestPartitionedTableRewriter\"", tableName);

    executeString(
      "create table " + canonicalTableName + "(col1 int4, col2 int4) partition by column(key float8) "
        + " as select l_orderkey, l_partkey, l_quantity from default.lineitem");

    TableDesc tableDesc = catalog.getTableDesc(getCurrentDatabase(), tableName);
    assertNotNull(tableDesc);

    // With a filter which checks a partition key column
    Expr expr = sqlParser.parse("SELECT * FROM " + canonicalTableName + " WHERE key <= 40.0 ORDER BY key");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SORT, projNode.getChild().getType());
    SortNode sortNode = projNode.getChild();

    assertEquals(NodeType.SELECTION, sortNode.getChild().getType());
    SelectionNode selNode = sortNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(3, filteredPaths.length);
    assertEquals("key=17.0", filteredPaths[0].getName());
    assertEquals("key=36.0", filteredPaths[1].getName());
    assertEquals("key=38.0", filteredPaths[2].getName());

    executeString("DROP TABLE " + canonicalTableName + " PURGE").close();
  }


  @Test
  public void testConstantFoldingWithStringFunctions() throws Exception {
    Expr expr = sqlParser.parse("SELECT * FROM " + MULTIPLE_PARTITION_TABLE_NAME
      + " WHERE key1 between lower('PART123') and lower('PART125') order by n_nationkey");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SORT, projNode.getChild().getType());
    SortNode sortNode = projNode.getChild();

    assertEquals(NodeType.SELECTION, sortNode.getChild().getType());
    SelectionNode selNode = sortNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(2, filteredPaths.length);

    assertEquals("key3=1", filteredPaths[0].getName());
    assertEquals("key2=supp123", filteredPaths[0].getParent().getName());
    assertEquals("key1=part123", filteredPaths[0].getParent().getParent().getName());

    assertEquals("key3=2", filteredPaths[1].getName());
    assertEquals("key2=supp123", filteredPaths[1].getParent().getName());
    assertEquals("key1=part123", filteredPaths[1].getParent().getParent().getName());
  }

  @Test
  public final void testConstantFoldingWithExpression() throws Exception {
    String tableName = name.getMethodName().toLowerCase();
    String canonicalTableName = CatalogUtil.getCanonicalTableName("\"TestPartitionedTableRewriter\"", tableName);

    executeString(
      "create table " + canonicalTableName + "(col1 int4, col2 int4) partition by column(key float8) "
        + " as select l_orderkey, l_partkey, l_quantity from default.lineitem");

    TableDesc tableDesc = catalog.getTableDesc(getCurrentDatabase(), tableName);
    assertNotNull(tableDesc);

    Expr expr = sqlParser.parse("SELECT * FROM " + canonicalTableName
      + " WHERE key between round(abs(35.0 + 1.0)) and round(abs(37.0 + 1.0)) ORDER BY key");
    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SORT, projNode.getChild().getType());
    SortNode sortNode = projNode.getChild();

    assertEquals(NodeType.SELECTION, sortNode.getChild().getType());
    SelectionNode selNode = sortNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(2, filteredPaths.length);
    assertEquals("key=36.0", filteredPaths[0].getName());
    assertEquals("key=38.0", filteredPaths[1].getName());

    executeString("DROP TABLE " + canonicalTableName + " PURGE").close();
  }

  @Test
  public final void testConstantFoldingWithDateFunctions() throws Exception {
    String tableName = name.getMethodName().toLowerCase();
    String canonicalTableName = CatalogUtil.getCanonicalTableName("\"TestPartitionedTableRewriter\"", tableName);

    String[] partitionKeys = new String[] {"20160315", "20160316", "20160317"};

    executeString(
      "create table " + canonicalTableName + "(col1 int4, col2 int4) partition by column(reg_date text) "
        + " as select l_orderkey, l_partkey" +
        ", case " +
        " when l_orderkey = 2 then '20160315' " +
        " when l_orderkey = 3 then '20160316' " +
        " else '20160317' end as reg_date" +
        " from default.lineitem");

    TableDesc tableDesc = catalog.getTableDesc(getCurrentDatabase(), tableName);
    assertNotNull(tableDesc);

    Expr expr = sqlParser.parse("SELECT * FROM " + canonicalTableName
      + " WHERE reg_date between  TO_CHAR( ADD_DAYS(TO_DATE('2016-03-14','YYYY-MM-DD'), -1) , 'YYYYMMDD') " +
      " AND TO_CHAR( ADD_DAYS(TO_DATE('2016-03-14','YYYY-MM-DD'), 1) , 'YYYYMMDD') ORDER BY reg_date");

    QueryContext defaultContext = LocalTajoTestingUtility.createDummyContext(testingCluster.getConfiguration());
    LogicalPlan newPlan = planner.createPlan(defaultContext, expr);
    LogicalNode plan = newPlan.getRootBlock().getRoot();

    assertEquals(NodeType.ROOT, plan.getType());
    LogicalRootNode root = (LogicalRootNode) plan;

    ProjectionNode projNode = root.getChild();

    assertEquals(NodeType.SORT, projNode.getChild().getType());
    SortNode sortNode = projNode.getChild();

    assertEquals(NodeType.SELECTION, sortNode.getChild().getType());
    SelectionNode selNode = sortNode.getChild();
    assertTrue(selNode.hasQual());

    assertEquals(NodeType.SCAN, selNode.getChild().getType());
    ScanNode scanNode = selNode.getChild();
    scanNode.setQual(selNode.getQual());

    PartitionedTableRewriter rewriter = new PartitionedTableRewriter();
    OverridableConf conf = CommonTestingUtil.getSessionVarsForTest();

    Path[] filteredPaths = rewriter.findFilteredPartitionPaths(conf, scanNode);
    assertNotNull(filteredPaths);

    assertEquals(1, filteredPaths.length);
    assertEquals("reg_date=" + partitionKeys[0], filteredPaths[0].getName());

    executeString("DROP TABLE " + canonicalTableName + " PURGE").close();
  }
}