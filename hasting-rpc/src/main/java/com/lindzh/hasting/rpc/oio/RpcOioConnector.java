package com.lindzh.hasting.rpc.oio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.lindzh.hasting.rpc.RpcObject;
import com.lindzh.hasting.rpc.exception.RpcException;
import com.lindzh.hasting.rpc.exception.RpcNetExceptionHandler;
import com.lindzh.hasting.rpc.net.AbstractRpcConnector;
import com.lindzh.hasting.rpc.utils.SSLUtils;
import org.apache.log4j.Logger;

import com.lindzh.hasting.rpc.utils.RpcUtils;

public class RpcOioConnector extends AbstractRpcConnector implements RpcNetExceptionHandler {
	
	private Socket socket;
	private DataInputStream dis;
	private DataOutputStream dos;
	private Logger logger = Logger.getLogger(RpcOioConnector.class);
	
	public RpcOioConnector(AbstractRpcOioWriter writer){
		super(writer);
		this.init();
	}
	
	public RpcOioConnector(){
		this(null);
	}
	
	private void init(){
		if(this.getRpcWriter()==null){
			this.setRpcWriter(new SimpleRpcOioWriter());
		}
	}
	
	public RpcOioConnector(Socket socket,AbstractRpcOioWriter writer){
		this(writer);
		this.socket = socket;
	}

	public void startService(){
		super.startService();
		try {
			if(socket==null){
				socket = SSLUtils.getSocketInstance(sslContext, sslMode);
				socket.connect(new InetSocketAddress(this.getHost(),this.getPort()));
			}
			InetSocketAddress remoteAddress = (InetSocketAddress)socket.getRemoteSocketAddress();
			remotePort = remoteAddress.getPort();
			remoteHost = remoteAddress.getAddress().getHostAddress();
			dis = new DataInputStream(socket.getInputStream());
			dos = new DataOutputStream(socket.getOutputStream());
			this.getRpcWriter().registerWrite(this);
			this.getRpcWriter().startService();
			new ClientThread().start();
			this.fireStartNetListeners();
		} catch (Exception e) {
			this.handleNetException(e);
		}
	}

	private class ClientThread extends Thread{
		@Override
		public void run() {
			while(!stop){
				RpcObject rpc = RpcUtils.readDataRpc(dis,RpcOioConnector.this);
				if(rpc!=null){
					rpc.setHost(remoteHost);
					rpc.setPort(remotePort);
					rpc.setRpcContext(rpcContext);
					fireCall(rpc);
				}
			}
		}
	}

	@Override
	public void stopService() {
		super.stopService();
		stop = true;
		RpcUtils.close(dis, dos);
		try {
			socket.close();
		} catch (IOException e) {
			//do nothing
		}
		rpcContext.clear();
		sendQueueCache.clear();
	}

	public DataOutputStream getOutputStream() {
		return dos;
	}

	@Override
	public void handleConnectorException(Exception e) {
		this.getRpcWriter().unRegWrite(this);
		this.stopService();
		logger.error("connection caught io exception close");
		throw new RpcException(e);
	}
}
