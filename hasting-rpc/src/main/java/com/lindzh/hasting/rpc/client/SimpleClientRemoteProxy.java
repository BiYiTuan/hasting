package com.lindzh.hasting.rpc.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.lindzh.hasting.rpc.RemoteCall;
import com.lindzh.hasting.rpc.RemoteExecutor;
import com.lindzh.hasting.rpc.RpcContext;
import com.lindzh.hasting.rpc.Service;
import com.lindzh.hasting.rpc.utils.RpcUtils;
import com.lindzh.hasting.rpc.utils.XAliasUtils;

public class SimpleClientRemoteProxy implements InvocationHandler,Service{

	private RemoteExecutor remoteExecutor;
	
	private ConcurrentHashMap<Class,String> versionCache = new ConcurrentHashMap<Class,String>();

	private ConcurrentHashMap<Class,String> groupCache = new ConcurrentHashMap<Class,String>();

	/**
	 * 应用
	 */
	private String application;
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {
		Class<?> service = method.getDeclaringClass();
		
		String name = method.getName();
		RemoteCall call = new RemoteCall(service.getName(),name);
		call.setArgs(args);
		String version = versionCache.get(service);
		if(version!=null){
			call.setVersion(version);
		}else{
			call.setVersion(RpcUtils.DEFAULT_VERSION);
		}

		String group = groupCache.get(service);
		if(group==null){
			group = RpcUtils.DEFAULT_GROUP;
		}
		call.setGroup(group);
		
		//加入上下文附件传送支持
		Map<String, Object> attachment = RpcContext.getContext().getAttachment();
		call.setAttachment(attachment);

		//客户点应用加入附件中
		call.getAttachment().put("Application",application);

		if(method.getReturnType()==void.class){
			remoteExecutor.oneway(call);
			return null;
		}
		return remoteExecutor.invoke(call);
	}

	public RemoteExecutor getRemoteExecutor() {
		return remoteExecutor;
	}

	public void setRemoteExecutor(RemoteExecutor remoteExecutor) {
		this.remoteExecutor = remoteExecutor;
	}
	
	public <Iface> Iface registerRemote(Class<Iface> remote){
		return this.registerRemote(remote, RpcUtils.DEFAULT_VERSION);
	}
	
	public <Iface> Iface registerRemote(Class<Iface> remote,String version){
		return this.registerRemote(remote, version,RpcUtils.DEFAULT_GROUP);
	}

	public <Iface> Iface registerRemote(Class<Iface> remote,String version,String group){
		Iface result = (Iface)Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{remote}, this);
		if(version==null){
			version = RpcUtils.DEFAULT_VERSION;
		}
		versionCache.put(remote, version);

		if(group==null){
			group = RpcUtils.DEFAULT_GROUP;
		}

		XAliasUtils.addServiceRefType(remote);

		groupCache.put(remote,group);
		return result;
	}

	@Override
	public void startService() {
		remoteExecutor.startService();
	}

	@Override
	public void stopService() {
		remoteExecutor.stopService();
	}

	public String getApplication() {
		return application;
	}

	public void setApplication(String application) {
		this.application = application;
	}
}
