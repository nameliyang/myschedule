package cn.uncode.schedule;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.ZooKeeper;

public class DeleteNode {

	private static final String connectString = "192.168.0.2:2181";

	private static final int sessionTimeout = 2000;

	private static ZooKeeper zookeeper = null;

	public static void main(String[] args) throws Exception {
		rmr("/clusterschedule");
	}
	
	   /**
     * 递归删除 因为zookeeper只允许删除叶子节点，如果要删除非叶子节点，只能使用递归
     * @param path
     * @throws IOException
     */
    public static void rmr(String path) throws Exception {
        ZooKeeper zk = getZookeeper();
        //获取路径下的节点
        List<String> children = zk.getChildren(path, false);
        for (String pathCd : children) {
            //获取父节点下面的子节点路径
            String newPath = "";
            //递归调用,判断是否是根节点
            if (path.equals("/")) {
                newPath = "/" + pathCd;
            } else {
                newPath = path + "/" + pathCd;
            }
            rmr(newPath);
        }
        //删除节点,并过滤zookeeper节点和 /节点
        if (path != null && !path.trim().startsWith("/zookeeper") && !path.trim().equals("/")) {
            zk.delete(path, -1);
            //打印删除的节点路径
            System.out.println("被删除的节点为：" + path);
        }
    }
    
    
    /**
     * 获取Zookeeper实例
     * @return
     * @throws IOException
     * @throws InterruptedException 
     * @throws NoSuchAlgorithmException 
     */
    public static ZooKeeper getZookeeper() throws IOException, InterruptedException, NoSuchAlgorithmException {
    	CountDownLatch latch = new CountDownLatch(1);
    	
        zookeeper = new ZooKeeper(connectString, sessionTimeout, new Watcher() {
			@Override
			public void process(WatchedEvent event) {
				if(event.getState() == KeeperState.SyncConnected){
					latch.countDown();
				}
			}
		});
        latch.await();
        String authString = "ScheduleAdmin"+ ":"+ "password";
        zookeeper.addAuthInfo("digest", authString.getBytes());
        return zookeeper;
    }
}
