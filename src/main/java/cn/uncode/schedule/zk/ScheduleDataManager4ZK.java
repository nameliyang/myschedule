package cn.uncode.schedule.zk;

import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import cn.uncode.schedule.DynamicTaskManager;
import cn.uncode.schedule.core.IScheduleDataManager;
import cn.uncode.schedule.core.ScheduleServer;
import cn.uncode.schedule.core.TaskDefine;

/**
 * zk实现类
 * 
 *
 */
public class ScheduleDataManager4ZK implements IScheduleDataManager {
	private static final transient Logger LOG = LoggerFactory.getLogger(ScheduleDataManager4ZK.class);
	
	private static final String NODE_SERVER = "server";
	private static final String NODE_TASK = "task";
	private static final long SERVER_EXPIRE_TIME = 5000 * 3;
	private Gson gson ;
	private ZKManager zkManager;
	private String pathServer;
	private String pathTask;
	private long zkBaseTime = 0;
	private long loclaBaseTime = 0;
	private Random random;
	
    public ScheduleDataManager4ZK(ZKManager aZkManager) throws Exception {
    	this.zkManager = aZkManager;
    	gson = new GsonBuilder().registerTypeAdapter(Timestamp.class,new TimestampTypeAdapter()).setDateFormat("yyyy-MM-dd HH:mm:ss").create();
		this.pathServer = this.zkManager.getRootPath() +"/" + NODE_SERVER;
		this.pathTask = this.zkManager.getRootPath() +"/" + NODE_TASK;
		this.random = new Random();
		if (getZooKeeper().exists(this.pathServer, false) == null) {
			ZKTools.createPath(getZooKeeper(),this.pathServer, CreateMode.PERSISTENT, this.zkManager.getAcl());
		}
		loclaBaseTime = System.currentTimeMillis();
        String tempPath = this.zkManager.getZooKeeper().create(this.zkManager.getRootPath() + "/systime",null, this.zkManager.getAcl(), CreateMode.EPHEMERAL_SEQUENTIAL);
        Stat tempStat = this.zkManager.getZooKeeper().exists(tempPath, false);
        zkBaseTime = tempStat.getCtime();
        ZKTools.deleteTree(getZooKeeper(), tempPath);
        if(Math.abs(this.zkBaseTime - this.loclaBaseTime) > 5000){
        	LOG.error("请注意，Zookeeper服务器时间与本地时间相差 ： " + Math.abs(this.zkBaseTime - this.loclaBaseTime) +" ms");
        }
	}	
	
	public ZooKeeper getZooKeeper() throws Exception {
		return this.zkManager.getZooKeeper();
	}

	public boolean refreshScheduleServer(ScheduleServer server) throws Exception {
		Timestamp heartBeatTime = new Timestamp(this.getSystemTime());
    	String zkPath = this.pathServer +"/" + server.getUuid();
    	if(this.getZooKeeper().exists(zkPath, false)== null){
    		//数据可能被清除，先清除内存数据后，重新注册数据
    		server.setRegister(false);
    		return false;
    	}else{
    		Timestamp oldHeartBeatTime = server.getHeartBeatTime();
    		server.setHeartBeatTime(heartBeatTime);
    		server.setVersion(server.getVersion() + 1);
    		String valueString = this.gson.toJson(server);
    		try{
    			this.getZooKeeper().setData(zkPath,valueString.getBytes(),-1);
    		}catch(Exception e){
    			//恢复上次的心跳时间
    			server.setHeartBeatTime(oldHeartBeatTime);
    			server.setVersion(server.getVersion() - 1);
    			throw e;
    		}
    		return true;
    	}
	}

	@Override
	public void registerScheduleServer(ScheduleServer server) throws Exception {
		if(server.isRegister()){
			throw new Exception(server.getUuid() + " 被重复注册");
		}
		//clearExpireScheduleServer();
		String realPath;
		//此处必须增加UUID作为唯一性保障
		StringBuffer id = new StringBuffer();
		id.append(server.getIp()).append("$")
			.append(UUID.randomUUID().toString().replaceAll("-", "").toUpperCase());
		String zkServerPath = pathServer + "/" + id.toString() +"$";
		realPath = this.getZooKeeper().create(zkServerPath, null, this.zkManager.getAcl(),CreateMode.PERSISTENT_SEQUENTIAL);
		server.setUuid(realPath.substring(realPath.lastIndexOf("/") + 1));
		
		Timestamp heartBeatTime = new Timestamp(getSystemTime());
		server.setHeartBeatTime(heartBeatTime);
		
		String valueString = this.gson.toJson(server);
		this.getZooKeeper().setData(realPath,valueString.getBytes(),-1);
		server.setRegister(true);
	}
	
	public List<String> loadAllScheduleServer() throws Exception {
		String zkPath = this.pathServer;
		List<String> names = this.getZooKeeper().getChildren(zkPath,false);
		Collections.sort(names);
		return names;
	}
	
	public void clearExpireScheduleServer() throws Exception{
		 String zkPath = this.pathServer;
		 if(this.getZooKeeper().exists(zkPath,false)== null){
			 this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
		 }
		for (String name : this.zkManager.getZooKeeper().getChildren(zkPath, false)) {
			try {
				Stat stat = new Stat();
				this.getZooKeeper().getData(zkPath + "/" + name, null, stat);
				if (getSystemTime() - stat.getMtime() > SERVER_EXPIRE_TIME) {
					ZKTools.deleteTree(this.getZooKeeper(), zkPath + "/" + name);
					LOG.debug("ScheduleServer[" + zkPath + "/" + name + "]过期清除");
				}
			} catch (Exception e) {
				// 当有多台服务器时，存在并发清理的可能，忽略异常
			}
		}
	}


	@Override
	public void unRegisterScheduleServer(ScheduleServer server) throws Exception {
		List<String> serverList = this.loadScheduleServerNames();

		if(server.isRegister() && this.isLeader(server.getUuid(), serverList)){
			//delete task
			String zkPath = this.pathTask;
			String serverPath = this.pathServer;

			if(this.getZooKeeper().exists(zkPath,false)== null){
				this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
			}

			//get all task
			List<String> children = this.getZooKeeper().getChildren(zkPath, false);
			if(null != children && children.size() > 0){
				for (String taskName : children) {
					String taskPath = zkPath + "/" + taskName;
					if (this.getZooKeeper().exists(taskPath, false) != null) {
						ZKTools.deleteTree(this.getZooKeeper(), taskPath + "/" + server.getUuid());
					}
				}
			}

			//删除
			if (this.getZooKeeper().exists(this.pathServer, false) == null) {
				ZKTools.deleteTree(this.getZooKeeper(), serverPath + serverPath + "/" + server.getUuid());
			}
			server.setRegister(false);
		}
	}
	
	public List<String> loadScheduleServerNames() throws Exception {
		String zkPath = this.pathServer;
		if (this.getZooKeeper().exists(zkPath, false) == null) {
			return new ArrayList<String>();
		}
		List<String> serverList = this.getZooKeeper()
				.getChildren(zkPath, false);
		Collections.sort(serverList, new Comparator<String>() {
			public int compare(String u1, String u2) {
				return u1.substring(u1.lastIndexOf("$") + 1).compareTo(
						u2.substring(u2.lastIndexOf("$") + 1));
			}
		});
		return serverList;
	}

	
	@Override
	public void assignTask(String currentUuid, List<String> taskServerList) throws Exception {
		 if(!this.isLeader(currentUuid, taskServerList)){
			 if(LOG.isDebugEnabled()){
				 LOG.debug(currentUuid +":不是负责任务分配的Leader,直接返回");
			 }
			 return;
		 }
		 if(LOG.isDebugEnabled()){
			 LOG.debug(currentUuid +":开始重新分配任务......");
		 }		
		 if(taskServerList.size()<=0){
			 //在服务器动态调整的时候，可能出现服务器列表为空的清空
			 return;
		 }
		 if(this.zkManager.checkZookeeperState()){
			 String zkPath = this.pathTask;
			 if(this.getZooKeeper().exists(zkPath,false)== null){
				 this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
			 }
			 List<String> children = this.getZooKeeper().getChildren(zkPath, false);
			 if(null != children && children.size() > 0){
				 for (String taskName : children) {
					 String taskPath = zkPath + "/" + taskName;
					 if (this.getZooKeeper().exists(taskPath, false) == null) {
						 this.getZooKeeper().create(taskPath, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
					 }

					 List<String> taskServerIds = this.getZooKeeper().getChildren(taskPath, false);
					 if (null == taskServerIds || taskServerIds.size() == 0) {
						 assignServer2Task(taskServerList, taskPath);
					 } else {
						 boolean hasAssignSuccess = false;
						 for (String serverId : taskServerIds) {
							 if (taskServerList.contains(serverId)) {
								 //防止重复分配任务，如果已经成功分配，第二个以后都删除
								 if(hasAssignSuccess){
									 ZKTools.deleteTree(this.getZooKeeper(), taskPath + "/" + serverId);
								 }else{
									 hasAssignSuccess = true;
									 continue; 
								 }
							 }
							 ZKTools.deleteTree(this.getZooKeeper(), taskPath + "/" + serverId);
						 }
						 if (!hasAssignSuccess) {
							 assignServer2Task(taskServerList, taskPath);
						 }
					 }

				 }	
			 }else{
				 if(LOG.isDebugEnabled()){
					 LOG.debug(currentUuid +":没有集群任务");
				 }	
			 }
		 }
		 
	}

	private void assignServer2Task(List<String> taskServerList, String taskPath) throws Exception {
		int index = random.nextInt(taskServerList.size());
		String serverId = taskServerList.get(index);
		try {
			if(this.getZooKeeper().exists(taskPath, false) != null){
				this.getZooKeeper().create(taskPath + "/" + serverId, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
			}
		} catch (Exception e) {
			LOG.error("assign task error");
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("Assign server [" + serverId + "]" + " to task [" + taskPath + "]");
		}
	}

	public boolean isLeader(String uuid,List<String> serverList){
    	return uuid.equals(getLeader(serverList));
    }
	
	private String getLeader(List<String> serverList){
		if(serverList == null || serverList.size() ==0){
			return "";
		}
		long no = Long.MAX_VALUE;
		long tmpNo = -1;
		String leader = null;
    	for(String server:serverList){
    		tmpNo =Long.parseLong( server.substring(server.lastIndexOf("$")+1));
    		if(no > tmpNo){
    			no = tmpNo;
    			leader = server;
    		}
    	}
    	return leader;
    }
	
	private long getSystemTime(){
		return this.zkBaseTime + ( System.currentTimeMillis() - this.loclaBaseTime);
	}
	
	private class TimestampTypeAdapter implements JsonSerializer<Timestamp>, JsonDeserializer<Timestamp>{
	    public JsonElement serialize(Timestamp src, Type arg1, JsonSerializationContext arg2) {   
	    	DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");   
	        String dateFormatAsString = format.format(new Date(src.getTime()));   
	        return new JsonPrimitive(dateFormatAsString);   
	    }   
	  
	    public Timestamp deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {   
	        if (!(json instanceof JsonPrimitive)) {   
	            throw new JsonParseException("The date should be a string value");   
	        }   
	  
	        try {   
	        	DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");   
	            Date date = (Date) format.parse(json.getAsString());   
	            return new Timestamp(date.getTime());   
	        } catch (Exception e) {   
	            throw new JsonParseException(e);   
	        }   
	    }
	}

	@Override
	public boolean isOwner(String name, String uuid) throws Exception {
		boolean isOwner = false;
		//查看集群中是否注册当前任务，如果没有就自动注册
		String zkPath = this.pathTask + "/" + name;
		//判断是否分配给当前节点
		if(this.getZooKeeper().exists(zkPath + "/" + uuid, false) != null){
			isOwner = true;
		}
		return isOwner;
	}
	
	
	@Override
	public boolean isRunning(String name) throws Exception {
		boolean isRunning = true;
		//查看集群中是否注册当前任务，如果没有就自动注册
		String zkPath = this.pathTask + "/" + name;
		//是否手动停止
		byte[] data = this.getZooKeeper().getData(zkPath, null, null);
		if (null != data) {
			 String json = new String(data);
			 TaskDefine taskDefine = this.gson.fromJson(json, TaskDefine.class);
			 if(taskDefine.isStop()){
				 isRunning = false;
			 }
		}
		return isRunning;
	}
	
	@Override
	public boolean saveRunningInfo(String name, String uuid) throws Exception {
		return saveRunningInfo(name, uuid, null);
	}

	
	@Override
	public boolean saveRunningInfo(String name, String uuid, String msg) throws Exception {
		//查看集群中是否注册当前任务，如果没有就自动注册
		String zkPath = this.pathTask + "/" + name;
		//判断是否分配给当前节点
		zkPath = zkPath + "/" + uuid;
		if(this.getZooKeeper().exists(zkPath,false) != null){
			try {
				int times = 0;
				byte[] dataVal = this.getZooKeeper().getData(zkPath, null, null);
				if(dataVal != null){
					String val = new String(dataVal);
					String[] vals = val.split(":");
					times = Integer.parseInt(vals[0]);
				}
				times++;
				String newVal = null;
				if(StringUtils.isNotBlank(msg)){
					newVal = "0:"+System.currentTimeMillis() + ":" + msg;
				}else{
					newVal = times+":"+System.currentTimeMillis();
				}
				this.getZooKeeper().setData(zkPath, newVal.getBytes(), -1);
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
		return true;
	}

	@Override
	public boolean isExistsTask(TaskDefine taskDefine) throws Exception{
		String zkPath = this.pathTask+ "/" + taskDefine.stringKey();
		return this.getZooKeeper().exists(zkPath, false) != null;
	}

	@Override
	public void addTask(TaskDefine taskDefine) throws Exception {
		String zkPath = this.pathTask;
		zkPath = zkPath + "/" + taskDefine.stringKey();
		if(this.getZooKeeper().exists(zkPath, false) == null){
			this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
		}
		byte[] data = this.getZooKeeper().getData(zkPath, null, null);
		if(null == data || data.length == 0){
			if(StringUtils.isBlank(taskDefine.getType())){
				taskDefine.setType(TaskDefine.TYPE_UNCODE_TASK);
			}
			String json = this.gson.toJson(taskDefine);
			this.getZooKeeper().setData(zkPath, json.getBytes(), -1);
		}
	}
	
	@Override
	public void updateTask(TaskDefine taskDefine) throws Exception {
		String zkPath = this.pathTask;
		zkPath = zkPath + "/" + taskDefine.stringKey();
		if(this.getZooKeeper().exists(zkPath, false) == null){
//			this.getZooKeeper().create(zkPath, null, this.zkManager.getAcl(), CreateMode.PERSISTENT);
//			String json = this.gson.toJson(taskDefine);
//			this.getZooKeeper().setData(zkPath, json.getBytes(), -1);
		}else{
			byte[] data = this.getZooKeeper().getData(zkPath, null, null);
			TaskDefine tmpTaskDefine = null;
			if (null != data) {
				 String json = new String(data);
				 tmpTaskDefine = this.gson.fromJson(json, TaskDefine.class);
				 tmpTaskDefine.valueOf(tmpTaskDefine);
			}else{
				tmpTaskDefine = new TaskDefine();
			}
			tmpTaskDefine.valueOf(taskDefine);
			String json = this.gson.toJson(tmpTaskDefine);
			this.getZooKeeper().setData(zkPath, json.getBytes(), -1);
		}
	}
	
	@Deprecated
	@Override
	public void delTask(String targetBean, String targetMethod) throws Exception {
		String zkPath = this.pathTask;
		if(this.getZooKeeper().exists(zkPath,false) != null){
			zkPath = zkPath + "/" + targetBean + "#" + targetMethod;
			if(this.getZooKeeper().exists(zkPath, false) != null){
				ZKTools.deleteTree(this.getZooKeeper(), zkPath);
			}
		}
	}
	
	@Override
	public void delTask(TaskDefine taskDefine) throws Exception {
		String zkPath = this.pathTask;
		if(this.getZooKeeper().exists(zkPath,false) != null){
			zkPath = zkPath + "/" + taskDefine.stringKey();
			if(this.getZooKeeper().exists(zkPath, false) != null){
				ZKTools.deleteTree(this.getZooKeeper(), zkPath);
			}
		}
	}
	
	@Override
	public List<TaskDefine> selectTask() throws Exception {
		String zkPath = this.pathTask;
		List<TaskDefine> taskDefines = new ArrayList<TaskDefine>();
		if(this.getZooKeeper().exists(zkPath,false) != null){
			List<String> childes = this.getZooKeeper().getChildren(zkPath, false);
			for(String child:childes){
				byte[] data = this.getZooKeeper().getData(zkPath+"/"+child, null, null);
				TaskDefine taskDefine = null;
				if (null != data) {
					 String json = new String(data);
					 taskDefine = this.gson.fromJson(json, TaskDefine.class);
				}else{
					taskDefine = new TaskDefine();
				}
				String[] names = child.split("#");
				if(StringUtils.isNotEmpty(names[0])){
					taskDefine.setTargetBean(names[0]);
					taskDefine.setTargetMethod(names[1]);
				}
				List<String> sers = this.getZooKeeper().getChildren(zkPath+"/"+child, false);
				if(taskDefine != null && sers != null && sers.size() > 0){
					taskDefine.setCurrentServer(sers.get(0));
					byte[] dataVal = this.getZooKeeper().getData(zkPath+"/"+child+"/"+sers.get(0), null, null);
					if(dataVal != null){
						String val = new String(dataVal);
						String[] vals = val.split(":");
						taskDefine.setRunTimes(Integer.valueOf(vals[0]));
						taskDefine.setLastRunningTime(Long.valueOf(vals[1]));
						if(vals.length > 2 && StringUtils.isNotBlank(vals[2])){
							taskDefine.setStatus(TaskDefine.STATUS_ERROR + ":" + vals[2]);
						}
					}
				}
				taskDefines.add(taskDefine);
			}
		}
		return taskDefines;
	}

	@Override
	public boolean checkLocalTask(String currentUuid) throws Exception {
		if(this.zkManager.checkZookeeperState()){
			 String zkPath = this.pathTask;
			 List<String> children = this.getZooKeeper().getChildren(zkPath, false);
			 List<String> ownerTask = new ArrayList<String>();
			 if(null != children && children.size() > 0){
				 for (String taskName : children) {
					 if (isOwner(taskName, currentUuid)) {
						 String taskPath = zkPath + "/" + taskName;
						 byte[] data = this.getZooKeeper().getData(taskPath, null, null);
						 if (null != data) {
							 String json = new String(data);
							 TaskDefine td = this.gson.fromJson(json, TaskDefine.class);
							 TaskDefine taskDefine = new TaskDefine();
							 taskDefine.valueOf(td);
							 if(TaskDefine.TYPE_UNCODE_TASK.equals(taskDefine.getType())){
								 ownerTask.add(taskName);
								 DynamicTaskManager.scheduleTask(taskDefine, new Date(getSystemTime()));
							 }
						 }
					 }
				 }
			 }
			 DynamicTaskManager.clearLocalTask(ownerTask);
		}
		return false;
	}


	

	

	


}