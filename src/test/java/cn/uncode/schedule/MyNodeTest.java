package cn.uncode.schedule;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MyNodeTest {
	public static void main(String[] args) throws InterruptedException {

		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				"myapplicationContext.xml");
		
		synchronized (MyNodeTest.class) {
			MyNodeTest.class.wait();
		}
		org.quartz.Job f;
		
	}
}
