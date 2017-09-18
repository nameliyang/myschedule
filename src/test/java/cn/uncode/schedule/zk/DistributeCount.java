package cn.uncode.schedule.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.data.Stat;

public class DistributeCount {

	private static final String ZK_ADDRESS = "127.0.0.1:2181";
	private static final String ZK_LOCK_PATH = "/LOCK_PATH";

	public static void main(String[] args) {
		CuratorFramework client = CuratorFrameworkFactory.newClient(ZK_ADDRESS,
				new RetryNTimes(10, 5000));
		client.start();
		
		InterProcessMutex lock = new InterProcessMutex(client, ZK_LOCK_PATH);
		Server serverA = new Server("serverA", client,lock);
		serverA.start();
		Server serverB = new Server("ServerB",client, lock);
		serverB.start();

	}
}

class Server extends Thread {

	private static final String PATH = "/count";

	 CuratorFramework client;

	private InterProcessMutex lock;

	public Server(String name,  CuratorFramework client,InterProcessMutex lock) {
		super(name);
		this.lock = lock;
		this.client = client;
	}

	@Override
	public void run() {
		for (int i = 0; i < 10; i++) {
			try {
				lock.acquire();
				try {
					Stat countPath = client.checkExists().forPath(PATH);
					if (countPath != null) {
						byte[] dataByte = client.getData().forPath(PATH);
						int count = Integer.parseInt(new String(dataByte));
						// Thread.sleep(new Random().nextInt(1000));
						System.out.println(Thread.currentThread().getName()
								+ ",count = " + count);
						client.setData().forPath(PATH,
								String.valueOf(count + 1).getBytes());
					} else {
						client.create().creatingParentsIfNeeded()
								.forPath(PATH, "1".getBytes());
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (Exception e1) {
				e1.printStackTrace();
			} finally {
				try {
					lock.release();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}

	}
}
