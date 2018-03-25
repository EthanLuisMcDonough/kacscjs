package configs;

public class PrivateConfigTemplate {
	// Your KA API keys
	// Register an application here: https://www.khanacademy.org/api-apps/register
	public static final String KA_CONSUMER_KEY = "<API KEY>";
	public static final String KA_CONSUMER_SECRET = "<API SECRET>";
	
	// Your JDBC connection string
	public static final String CONNECTION_STRING = "jdbc:mysql://localhost:3306/kacscjs";
	public static final String USERNAME = "<USERNAME>"; // the database user
	public static final String PASSWORD = "<PASSWORD>"; // the user's password
}
