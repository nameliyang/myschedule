package cn.uncode.schedule;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;

public class Test {
	public static void main(String[] args) throws InterruptedException, IOException {
		
		MyZkCilent client = new MyZkCilent("127.0.0.1:2181");
		client.connnect();
		TimeUnit.SECONDS.sleep(100);
	}
}

class MyZkCilent implements Watcher {

	private String server;

	private ZooKeeper zookeeper;

	private CountDownLatch latch = new CountDownLatch(1);

	public MyZkCilent(String server) {
		this.server = server;
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