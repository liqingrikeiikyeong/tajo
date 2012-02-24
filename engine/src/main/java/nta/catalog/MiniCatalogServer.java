/**
 * 
 */
package nta.catalog;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;

/**
 * @author Hyunsik Choi
 *
 */
public class MiniCatalogServer {
  private CatalogServer catalogServers;
  
  public MiniCatalogServer(Configuration conf) throws IOException {
    this.catalogServers = new CatalogServer(conf);
    this.catalogServers.start();
  }
  
  public void shutdown() {
    this.catalogServers.shutdown("Normally shuting down");
  }
  
  public CatalogServer getCatalogServer() {
    return this.catalogServers;
  }
  
  public CatalogService getCatalog() {
    return new LocalCatalog(this.catalogServers);
  }
}
