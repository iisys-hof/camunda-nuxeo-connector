package de.hofuniversity.iisys.camunda.workflows.nuxeo;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class NuxeoConfig
{
	public static final String PROPERTIES = "camunda-nuxeo-connector";
	
	public static final String NUXEO_URL = "nuxeo.url";

	public static final String DEBUG_USER = "debug.user";
	public static final String DEBUG_PASSWORD = "debug.password";
	
	private static NuxeoConfig fInstance;
	
	private final Map<String, String> fConfig;
	
	public static synchronized NuxeoConfig getInstance()
	{
		if(fInstance == null)
		{
			fInstance = new NuxeoConfig();
		}
		
		return fInstance;
	}
	
	public NuxeoConfig()
	{
		fConfig = new HashMap<String, String>();
		
		readConfig();
	}
	
	private void readConfig()
	{
		try
		{
	        final ClassLoader loader = Thread.currentThread()
	            .getContextClassLoader();
	        ResourceBundle rb = ResourceBundle.getBundle(PROPERTIES,
	            Locale.getDefault(), loader);
	        
	        String key = null;
	        String value = null;
	        
	        Enumeration<String> keys = rb.getKeys();
	        while(keys.hasMoreElements())
	        {
	        	key = keys.nextElement();
	        	value = rb.getString(key);
	        	
	        	fConfig.put(key, value);
	        }
		}
		catch(Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	
	public Map<String, String> getConfiguration()
	{
		return fConfig;
	}
}
