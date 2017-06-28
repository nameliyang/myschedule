package cn.uncode.schedule.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import cn.uncode.schedule.ZKScheduleManager;
import cn.uncode.schedule.quartz.MethodInvokingJobDetailFactoryBean;

/**
 * Created by KevinBlandy on 2017/2/28 14:11
 */
@Configuration
@EnableConfigurationProperties({UncodeScheduleConfig.class})
public class UncodeScheduleAutoConfiguration {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(UncodeScheduleAutoConfiguration.class);
	
	@Autowired
	private UncodeScheduleConfig uncodeScheduleConfig;
	
	@Bean(name = "zkScheduleManager", initMethod="init")
	public ZKScheduleManager commonMapper(){
		ZKScheduleManager zkScheduleManager = new ZKScheduleManager();
		zkScheduleManager.setZkConfig(uncodeScheduleConfig.getConfig());
		LOGGER.info("=====>ZKScheduleManager inited..");
		return zkScheduleManager;
	}
	
	@Bean(name = "quartzSchedulerFactoryBean")
	public SchedulerFactoryBean quartzSchedulerFactoryBean(){
		SchedulerFactoryBean schedulerFactoryBean = new SchedulerFactoryBean();
		if(uncodeScheduleConfig.getQuartzBean() != null 
				&& uncodeScheduleConfig.getQuartzMethod() != null 
				&& uncodeScheduleConfig.getQuartzCronExpression() != null){
			int len = uncodeScheduleConfig.getQuartzBean().size();
			List<Trigger> triggers = new ArrayList<Trigger>();
			for(int i=0; i<len; i++){
				String name = uncodeScheduleConfig.getQuartzBean().get(i);
				String method = uncodeScheduleConfig.getQuartzMethod().get(i);
				String cronExpression = uncodeScheduleConfig.getQuartzCronExpression().get(i);
				if(StringUtils.isNotBlank(name) && StringUtils.isNotBlank(method) && StringUtils.isNotBlank(cronExpression)){
					MethodInvokingJobDetailFactoryBean methodInvokingJob = new MethodInvokingJobDetailFactoryBean();
					methodInvokingJob.setTargetBeanName(name);
					methodInvokingJob.setTargetMethod(method);
					CronTriggerFactoryBean cronTrigger = new CronTriggerFactoryBean();
					cronTrigger.setJobDetail(methodInvokingJob.getObject());
					triggers.add(cronTrigger.getObject());
				}
			}
			if(triggers != null && triggers.size() > 0){
				schedulerFactoryBean.setTriggers((Trigger[])triggers.toArray());
			}
		}
		LOGGER.info("=====>QuartzSchedulerFactoryBean inited..");
		return schedulerFactoryBean;
	}
	
	
	
}
