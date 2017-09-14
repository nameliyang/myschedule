package cn.uncode.schedule;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooKeeper;

public class Test {
	public static void main(String[] args) throws InterruptedException, IOException {
		MyZkCilent client = new MyZkCilent("127.0.0.1:2181");
		client.connnect();
		
		client.create("/test");
	}
}

class MyZkCilent implements Watcher {

	private String server;

	private ZooKeeper zookeeper;

	private CountDownLatch latch = new CountDownLatch(1);

	public MyZkCilent(String server) {
		this.server = server;
	}

	public void create(String path){
		try {
			while(zookeeper.exists(path, false) == null){
				zookeeper.create(path, path.getBytes(),  Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			}
		} catch (KeeperException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}

	public void connnect() throws IOException, InterruptedException {
		zookeeper = new ZooKeeper(server, 5000, this);
		latch.await();
	}

	@Override
	public void process(WatchedEvent event) {
		
		if (event.getState() == KeeperState.SyncConnected) {
			System.out.println("zk client success....");
			latch.countDown();
		}
		
	}

}