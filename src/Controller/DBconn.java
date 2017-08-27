package Controller;

import javax.sql.RowSet;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by rocks on 4/30/2017.
 */
public class DBconn {
    public class Record{
        public int getId() {
            return id;
        }

        public int getTime() {
            return time;
        }

        public long getHash() {
            return hash;
        }

        int id;
        int time;
        long hash;
    }
    private Connection dbConn;
    private Statement dbStatement;
    private PreparedStatement insertMusic;

    private final String driver = "com.mysql.jdbc.Driver";
    private final String url = "jdbc:mysql://127.0.0.1:3306/musiclibrary";
    private final String user = "root";
    private final String password = "";

    public int id=0;

    public DBconn() {
        super();

        try {
            Class.forName(driver);
            dbConn = DriverManager.getConnection(url, user, password);
            if (dbConn.isClosed())
                throw new Exception("can not open Database");
            dbStatement = dbConn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            dbStatement.setFetchSize(Integer.MIN_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void insertMusic(String musicTitle,String showTitle) throws Exception
    {
        musicTitle= musicTitle.toLowerCase();
        showTitle= showTitle.toLowerCase();
        String query="insert into musicinfo (title,shows) values ('"+musicTitle+"','"+showTitle+"')";
        dbStatement.execute(query);

        query="select * from musicinfo where title='"+musicTitle+"' and shows='"+showTitle+"'";
        ResultSet rs= dbStatement.executeQuery(query);
        rs.next();
        id=rs.getInt("idMusicInfo");
        rs.close();
       // dbConn=DriverManager.getConnection(url, user, password);
        System.out.println(id);
    }
    public void insertHash(Long hash,int time) throws Exception
    {
        String query="insert into hashtable (Hash,ID,Time) values ('"+hash+"',"+id+","+time+")";
        dbStatement.execute(query);
    }
    public ArrayList<Record> returnAllHash() throws Exception
    {
        String query="select * from hashtable";
        ArrayList<Record> records=new ArrayList<>();
        Statement sm=dbConn.createStatement();
        ResultSet resultSet= sm.executeQuery(query);
        System.out.print("in return hash");

        while (resultSet.next())
        {
            Record r=new Record();
            r.id=resultSet.getInt("ID");

            r.time=resultSet.getInt("Time");
            r.hash=Long.parseLong(resultSet.getString("Hash"));
            records.add(r);

        }
        resultSet.close();
        return records;
    }
    public HashMap<Integer,String> returnSongInfo() throws Exception
    {
        String query="select * from musicinfo";
        HashMap<Integer,String> records=new HashMap<>();
        Statement sm=dbConn.createStatement();
        ResultSet resultSet= sm.executeQuery(query);
        while (resultSet.next())
        {
        String value=resultSet.getString("title")+":"+resultSet.getString("shows");
            records.put(resultSet.getInt("idMusicInfo"),value);

        }
        resultSet.close();
        return records;

    }

    public static void main(String[] args) throws Exception {
        DBconn dBconn=new DBconn();
        dBconn.returnAllHash();
    }

}
