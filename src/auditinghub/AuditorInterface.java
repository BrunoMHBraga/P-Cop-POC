package auditinghub;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import admin.AdminInterface;
import admin.CTRLCHandler;
import exceptions.InvalidMessageException;
import global.Messages;
import global.Ports;

//Auditors responsible for checking the logs may use this interface to commit them. For now commit is just putting them on another folder
//In the future will unlock untrusted nodes.
//Usage: AuditorInterface -m monitor

public class AuditorInterface {

	private static final String UNCOMMITED_LOGS_DIR="../../Logs/Uncommited/";
	private static final String COMMITED_LOGS_DIR="../../Logs/Commited/";
	private static final int MONITOR_FLAG_INDEX = 0;
	private String monitorHost;

	public AuditorInterface(String monitorHost) {
		this.monitorHost = monitorHost;
	}
	
	private boolean setTrusted(String hostName) throws InvalidMessageException, UnknownHostException, IOException {
		
		String setTrustedRequestString = String.format("%s %s", Messages.SET_TRUSTED,InetAddress.getByName(hostName).getHostAddress());

		Socket monitorSocket =  new Socket(this.monitorHost, Ports.MONITOR_HUB_PORT);
		BufferedReader setTrustedSessionReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		BufferedWriter setTrustedSessionWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));

		setTrustedSessionWriter.write(setTrustedRequestString);
		setTrustedSessionWriter.newLine();
		setTrustedSessionWriter.flush();

		//This detects when the socket is closed....If null response....
		String response = setTrustedSessionReader.readLine();
		switch(response){
		case Messages.OK:
			System.out.println("Success on setting node as trusted.");
			return true;
		case Messages.ERROR:
			return false;
		default:
			throw new InvalidMessageException("Invalide response:" + response);
		}
	}

	public static void main(String[] args) throws IOException{
		
		AuditorInterface interfaceInstance = null;
		String monitorHost;
		switch (args.length){
		case 2:
			monitorHost = args[MONITOR_FLAG_INDEX+1];
			interfaceInstance = new AuditorInterface(monitorHost);
			break;
		default:
			System.out.println("Usage: AuditorInterface -m monitorHost");
			System.exit(0);
			break;
		}

		Path commitedLogsDirPath = FileSystems.getDefault().getPath(COMMITED_LOGS_DIR);
		if(Files.notExists(commitedLogsDirPath))
			Files.createDirectories(commitedLogsDirPath);

		Path uncommitedLogsDirPath = FileSystems.getDefault().getPath(UNCOMMITED_LOGS_DIR);

		Stream<Path> uncommitedLogsStream = null; 
		Stream<Path> commitedLogsStream = null;

		BufferedReader promptReader= new BufferedReader(new InputStreamReader(System.in));
		


		while (true){
			System.out.println("Available commands:");
			System.out.println("List uncommited logs:lu");
			System.out.println("List commited logs:lc");
			System.out.println("Read uncommited log:r log_id");
			System.out.println("Commit log:c log_id");
			System.out.println("Exit:e");
			System.out.print(">");

			String auditorCommand = promptReader.readLine();
			String[] splittedCommand = auditorCommand.split(" ");
			switch (splittedCommand[0]) {
			case "lu":
				//what if . instead of ::??
				uncommitedLogsStream = Files.find(uncommitedLogsDirPath, 1,  (P,A)->P.toString().matches(".*.log"));
				uncommitedLogsStream.forEach(P->System.out.println(P.getFileName()));
				uncommitedLogsStream.close();
				break;
			case "lc":
				commitedLogsStream = Files.find(commitedLogsDirPath, 1,  (P,A)->P.toString().matches(".*.log"));
				commitedLogsStream.forEach(P->System.out.println(P.getFileName()));
				commitedLogsStream.close();
				break;
			case "r":
				uncommitedLogsStream = Files.find(uncommitedLogsDirPath, 1, (P,A)->P.toString().matches(".*.log"));
				List<String> logLines = Files.readAllLines(uncommitedLogsStream.filter(P->P.getFileName().toString().matches(splittedCommand[1])).findFirst().get());
				logLines.stream().forEach(System.out::println);
				uncommitedLogsStream.close();
				break;

			case "c":
				uncommitedLogsStream = Files.find(uncommitedLogsDirPath, 1,  (P,A)->P.toString().matches(".*.log"));
				Path oldLogPath = uncommitedLogsStream.filter(P->P.getFileName().toString().matches(splittedCommand[1])).findFirst().get();
				Pattern hostNamePattern = Pattern.compile("@(.+)__");
				String logName = oldLogPath.getFileName().toString();
			    Matcher hostNameMatcher = hostNamePattern.matcher(logName);
			    if(!hostNameMatcher.find()){
			    	System.out.println("No hostname for the log.");
			    	System.exit(1);
			    }
			    String hostName = logName.substring(hostNameMatcher.start(1),hostNameMatcher.end(1));
			    try {
					if(!interfaceInstance.setTrusted(hostName))
					{
						System.out.println("Failed to set node trusted");
						System.exit(1);
					}
				} catch (InvalidMessageException e) {
					System.err.println("Failed set node:" + hostName + " as trusted.");
					System.exit(1);
				}
				//oldLogPath.getFileName().toString().
				Path newLogPath = FileSystems.getDefault().getPath(commitedLogsDirPath.toString(),oldLogPath.getFileName().toString());
				Files.move(oldLogPath,newLogPath);
				uncommitedLogsStream.close();
				break;
			case "e":
				System.exit(0);
			default:
				System.out.println("Invalid command.");
				break;
			}


		}



	}


}