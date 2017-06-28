package cn.uncode.schedule.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "uncode.schedule",ignoreInvalidFields = true)
public class UncodeScheduleConfig{
	
	private String zkConnect;
	private String rootPath = "/uncode/schedule";
	private int zkSessionTimeout = 60000;
	private String zkUsername;
	private String zkPassword;
	private List<String> ipBlackList;
	
	
	private List<String> quartzBean;
	private List<String> quartzMethod;
	private List<String> quartzCronExpression;
	
	
	public Map<String, String> getConfig(){
		Map<String, String> properties = new HashMap<String, String>();
		properties.put("zkConnectString", zkConnect);
		if(StringUtils.isNotBlank(rootPath)){
			properties.put("rootPath", rootPath);
		}
		if(zkSessionTimeout > 0){
			properties.put("zkSessionTimeout", zkSessionTimeout+"");
		}
		if(StringUtils.isNotBlank(zkUsername)){
			properties.put("userName", zkUsername);
		}
		if(StringUtils.isNotBlank(zkPassword)){
			properties.put("password", zkPassword);
		}
		StringBuilder sb = new StringBuilder();
		if(ipBlackList != null && ipBlackList.size() > 0){
			for(String ip:ipBlackList){
				sb.append(ip).append(",");
			}
			ipBlackList.remove(sb.lastIndexOf(","));
		}
		properties.put("ipBlacklist", sb.toString());
		return properties;
	}
	
	
	public String getZkConnect() {
		return zkConnect;
	}
	public void setZkConnect(String zkConnect) {
		this.zkConnect = zkConnect;
	}
	public String getRootPath() {
		return rootPath;
	}
	public void setRootPath(String rootPath) {
		this.rootPath = rootPath;
	}
	public int getZkSessionTimeout() {
		return zkSessionTimeout;
	}
	public void setZkSessionTimeout(int zkSessionTimeout) {
		this.zkSessionTimeout = zkSessionTimeout;
	}
	public String getZkUsername() {
		return zkUsername;
	}
	public void setZkUsername(String zkUsername) {
		this.zkUsername = zkUsername;
	}
	public String getZkPassword() {
		return zkPassword;
	}
	public void setZkPassword(String zkPassword) {
		this.zkPassword = zkPassword;
	}
	public List<String> getIpBlackList() {
		return ipBlackList;
	}
	public void setIpBlackList(List<String> ipBlackList) {
		this.ipBlackList = ipBlackList;
	}


	public List<String> getQuartzBean() {
		return quartzBean;
	}


	public void setQuartzBean(List<String> quartzBean) {
		this.quartzBean = quartzBean;
	}


	public List<String> getQuartzMethod() {
		return quartzMethod;
	}


	public void setQuartzMethod(List<String> quartzMethod) {
		this.quartzMethod = quartzMethod;
	}


	public List<String> getQuartzCronExpression() {
		return quartzCronExpression;
	}


	public void setQuartzCronExpression(List<String> quartzCronExpression) {
		this.quartzCronExpression = quartzCronExpression;
	}
	
	

}
