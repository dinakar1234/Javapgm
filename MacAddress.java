/**
 * 
 */
package com;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;

import javax.swing.JOptionPane;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.DataSourceConfig;
import com.avaje.ebean.config.ServerConfig;
import com.yatech.payment.cable.ui.CableSplashScreen;
import com.yatech.payment.cable.ui.LoginScreen;
import com.yatech.payment.cable.ui.PaymentMainFrame;
import com.yatech.payment.cable.util.Hasher;

/**
 * @author yatech
 * 
 */
public class MacAddress {
	private static String ACT_SEP = "|$|";
	
	
	public static String getMotherboardSN() {
		String result = "";
		try {
			File file = File.createTempFile("realhowto", ".vbs");
			file.deleteOnExit();
			FileWriter fw = new java.io.FileWriter(file);

			String vbs = "Set objWMIService = GetObject(\"winmgmts:\\\\.\\root\\cimv2\")\n"
					+ "Set colItems = objWMIService.ExecQuery _ \n"
					+ "   (\"Select * from Win32_BaseBoard\") \n"
					+ "For Each objItem in colItems \n"
					+ "    Wscript.Echo objItem.SerialNumber \n"
					+ "    exit for  ' do the first cpu only! \n" + "Next \n";

			fw.write(vbs);
			fw.close();
			Process p = Runtime.getRuntime().exec(
					"cscript //NoLogo " + file.getPath());
			BufferedReader input = new BufferedReader(new InputStreamReader(p
					.getInputStream()));
			String line;
			while ((line = input.readLine()) != null) {
				result += line;
			}
			input.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result.trim();
	}
	
	public static void updateActivationCode(String code)throws Exception{
		String act = "act";
		Properties hashProp = new Properties();
		File file = new File("hash.prop");
		hashProp.load(new FileReader(file));
		hashProp.setProperty(act,code);
		hashProp.store(new FileWriter(file), "Yatech Cable Systems");
	}

	public static String getActivationSMSCode(String dealerCode)throws Exception{
		String msn =  "msn";
		String idate = "idate";
		Properties hashProp = new Properties();
		File file = new File("hash.prop");
		hashProp.load(new FileReader(file));
		String actSMSCode = Hasher.decryptData(hashProp.getProperty(msn),"832205")+ACT_SEP+
		Hasher.decryptData(hashProp.getProperty(idate),"832205")+ACT_SEP+PaymentMainFrame.SERIAL_NUMBER+
		ACT_SEP+dealerCode;
		return Hasher.encryptData(actSMSCode,"832205");
	}

	public static void main2(String[] args)throws Exception {
		String msn =  "msn";
		String idate = "idate";
		String act = "act";
		Properties hashProp = new Properties();
		File file = new File("hash.prop");
		if(!file.exists()){
			// First Time,  create & init DB
			CableSplashScreen.getInstance().nextStep("First Time installations in progress...");
			Thread.sleep(1000);
			CableSplashScreen.getInstance().nextStep("Creating Database tables...");
			createDB();
			CableSplashScreen.getInstance().nextStep("Updating details ...");
			hashProp.setProperty(msn,Hasher.encryptData(MacAddress.getMotherboardSN(),"832205"));
			hashProp.setProperty(idate,Hasher.encryptData(System.currentTimeMillis()+"","832205"));
			hashProp.store(new FileWriter(file), "Yatech Cable Systems");
			CableSplashScreen.getInstance().splashDispose();
			JOptionPane.showMessageDialog(null, "First time setup is done. Please restart again","Cable Payment",JOptionPane.INFORMATION_MESSAGE);
		}else{
			//verify
			hashProp.load(new FileReader(file));
			String storedMSN  = Hasher.decryptData(hashProp.getProperty(msn),"832205");
			String actualMSN  = MacAddress.getMotherboardSN();
			if(storedMSN.equals(actualMSN)){
				String storedIDate = Hasher.decryptData(hashProp.getProperty(idate),"832205");
				PaymentMainFrame.INSTALLED_DATE = new Date(Long.parseLong(storedIDate));
				long diff = new Date().getTime()- PaymentMainFrame.INSTALLED_DATE.getTime();
				if(diff<0){
					JOptionPane.showMessageDialog(null, "Improper installation date. Contact us cableservice@yatech.in","Cable Payment",JOptionPane.ERROR_MESSAGE);
					System.exit(0);
				}
				long diffDays = diff / (24 * 60 * 60 * 1000);
				PaymentMainFrame.DEMO_PERIOD_AVAILABLE = (int)(60 - diffDays);
				if(PaymentMainFrame.DEMO_PERIOD_AVAILABLE<1){
					JOptionPane.showMessageDialog(null, "Your demo period is over. Contact us cableservice@yatech.in","Cable Payment",JOptionPane.ERROR_MESSAGE);
					System.exit(0);
				}
				String actDetails = hashProp.getProperty(act);
				if(actDetails!=null){
					actDetails = Hasher.decryptData(actDetails,"832205");
					//serial number + msn + act code
//					4CB94005W7|$|1304882947213|$|IDpL-VNqx-Q3Qy-bN0I|$|100
					//100 - success else extended days
					//201 - Invalid dealer code
					//202 - Serial number already activated
					ArrayList<String> list = new ArrayList<String>();
					while(true){
						int i = actDetails.indexOf(ACT_SEP);
						if(i>0){
							list.add(actDetails.substring(0,i));
							actDetails = actDetails.substring(i+3);
						}else{
							list.add(actDetails);
							break;
						}
					}
					if(list.size()>=4){//valid
						String actmsn = list.get(0);
						String actiDate = list.get(1);
						String actSN = list.get(2);
						String actRespCode = list.get(3);
						if(actualMSN.equals(actmsn) && storedIDate.equals(actiDate) && PaymentMainFrame.SERIAL_NUMBER.equals(actSN)){
							if(actRespCode.equals("100")){
								PaymentMainFrame.isACTIVATED = true;
							}
							if(actRespCode.equals("201")){
								PaymentMainFrame.ACT_ERR = "Invalid dealer code";
							}
							if(actRespCode.equals("202")){
								PaymentMainFrame.ACT_ERR = "Serial number already activated";
							}
							if(actRespCode.equals("203")){
								PaymentMainFrame.ACT_ERR = "Activation failed";
							}
						}else{//either msn or idate or sn is mismatch
							JOptionPane.showMessageDialog(null, "Activation details mismatch. Contact us cableservice@yatech.in","Cable Payment",JOptionPane.ERROR_MESSAGE);
							System.exit(0);
						}
					}else{// act details content is not proper
						JOptionPane.showMessageDialog(null, "Activation details mismatch. Contact us cableservice@yatech.in","Cable Payment",JOptionPane.ERROR_MESSAGE);
						System.exit(0);
					}
				}
				try {
					new LoginScreen();
				} catch (Exception e) {
				}
			}else{//actual msn and stored msn is different
				JOptionPane.showMessageDialog(null, "Serial number doesnot match. Contact us: cableservice@yatech.in","Cable Payment",JOptionPane.ERROR_MESSAGE);
				System.exit(0);
			}
		}

	}
	
	public static void   verifyActivation(){
		Properties hashProp = new Properties();
		File file = new File("hash.prop");
		try {
			hashProp.load(new FileReader(file));
		} catch (FileNotFoundException e1) {
		} catch (IOException e1) {
		}
//		String serial=Hasher.encryptData("serial");
		String serialNumber  = Hasher.decryptData(hashProp.getProperty("71d36408776871"),"832205");
		System.out.println(serialNumber);
//		if(serialNumber.equals(PaymentMainFrame.SERIAL_NUMBER)){
			String storedIDate = Hasher.decryptData(hashProp.getProperty("1119f3678771"),"832205");
			PaymentMainFrame.INSTALLED_DATE = new Date(Long.parseLong(storedIDate));
			long diff = new Date().getTime()- PaymentMainFrame.INSTALLED_DATE.getTime();
			if(diff<0){
				JOptionPane.showMessageDialog(null, "Improper installation date. Contact us cableservice@yatech.in","Cable Payment",JOptionPane.ERROR_MESSAGE);
				System.exit(0);
			}
			long diffDays = diff / (24 * 60 * 60 * 1000);
			PaymentMainFrame.DEMO_PERIOD_AVAILABLE = (int)(15 - diffDays);
			if(PaymentMainFrame.DEMO_PERIOD_AVAILABLE<1){
				JOptionPane.showMessageDialog(null, "Your demo period is over. Contact us cableservice@yatech.in","Cable Payment",JOptionPane.ERROR_MESSAGE);
				System.exit(0);
			}
//			String act=Hasher.encryptData("act");
			try{
			String actDetails = hashProp.getProperty("54547781");
			if(actDetails!=null){
				actDetails = Hasher.decryptData(actDetails,"832205");
				//serial number + msn + act code
//				4CB94005W7|$|1304882947213|$|IDpL-VNqx-Q3Qy-bN0I|$|100
				//100 - success else extended days
				//201 - Invalid dealer code
				//202 - Serial number already activated
			
				
						if(actDetails.equals("100")){
							PaymentMainFrame.isACTIVATED = true;
						}
						if(actDetails.equals("201")){
							PaymentMainFrame.ACT_ERR = "Invalid dealer code";
						}
						if(actDetails.equals("202")){
							PaymentMainFrame.ACT_ERR = "Serial number already activated";
						}
						if(actDetails.equals("203")){
							PaymentMainFrame.ACT_ERR = "Activation failed";
						}
					
				
				}
			}catch(Exception e){}
	
			
			try {
				new LoginScreen();
			} catch (Exception e) {
			}
//		}else{//actual msn and stored msn is different
//			JOptionPane.showMessageDialog(null, "Serial number doesnot match. Contact us: cableservice@yatech.in","Cable Payment",JOptionPane.ERROR_MESSAGE);
//			System.exit(0);
//		}
	}
	public static void createDB(){
		ServerConfig config = new ServerConfig();  
		config.setName("mysql");  
		  
		// Define DataSource parameters  
		DataSourceConfig postgresDb = new DataSourceConfig();  
		postgresDb.setDriver("com.mysql.jdbc.Driver");  
		postgresDb.setUsername("root");  
		postgresDb.setPassword("root");  
		postgresDb.setUrl("jdbc:mysql://localhost:3306/cable");
		config.setDataSourceConfig(postgresDb);  
		config.setDdlGenerate(true);  
		config.setDdlRun(true);  
		config.setDefaultServer(false);  
		config.setRegister(false);  
		EbeanServer server = EbeanServerFactory.create(config); 
	}

	
	
}