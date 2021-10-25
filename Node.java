import java.io.*;
import java.util.*;
import java.util.Timer;
import java.util.TimerTask;

public class Node { 

    private static int net_clock = 0;
    private static int node_id;
    private static int life;
    private static int dest_id;
    private static String message;
    private static int start_time;
    private static ArrayList<Integer> neighbors = new ArrayList<Integer>();
    private static ArrayList<String> r = new ArrayList<String>();
    private static String nack_num;
    private static String[] stored_messages = new String[1000];
    private static int[] sources = new int[100];
    private static int msg_counter = 0;
    private static int nack_to_send = 0;
    private static int nack_received = 0;
    private static boolean msg_flag = false;
    private static final long start = System.currentTimeMillis();

    public static void datalink_receive_from_network(char[] msg, int next_hop) throws IOException {

        String msg_to_send = new String(msg);
        char hop = (char) (next_hop + '0');
        //append the message
        File myFile = new File("from" + node_id + "to" + hop + ".txt");
        try (BufferedWriter WriteFile = new BufferedWriter(new FileWriter(myFile,true))) {
            WriteFile.write(msg_to_send);
            WriteFile.write("\n");
        }
    }

    public static void datalink_receive_from_channel() throws InterruptedException, IOException {

        //check every file sent by every neighbor of the current node
        //once you have received a full message, send it out
        for (int i = 0; i < neighbors.size(); i++) {
            File myFile = new File("from" + neighbors.get(i)+ "to" + node_id + ".txt");
            boolean exists = myFile.exists();
            if (exists) {
                FileReader fr = new FileReader(myFile);
                try (BufferedReader br = new BufferedReader(fr)) {
                    int bound = 0;
                    char[] mes = null;
                    boolean checksum = false;
                    int count = 0;
                    int new_mes = 0;
                    String seq_num;
                    String num = null;
                    String num1 = null;
                    //while you have not reached an end-line, continue reading through the file and copying the message
                    for(String str; (str = br.readLine()) != null;) {
                       
                        String s = str;
                        checksum = false;
                        //check to see if we have a complete message if we do, send it
                        if(s.length() <= 19 && s.contains("XX")) {

                            
                            num = s.substring(9,9);
                            num1 = s.substring(10,10);
                            String node = Integer.toString(node_id);
                            
                            

                            //if the message is a nack, count it
                            if (s.substring(0,6).contains("N") && s.contains(node)) {
                                seq_num = num + num1;
                                nack_num = seq_num;
                                nack_received++;
                            }

                            mes = s.toCharArray();
                            int check = 0;

                            for(int j = 2; j < mes.length - 2; j++) {
                                check += mes[j];
                            }
                            String sub = s.substring(s.length() - 2);
                            check = check % 100;

                            String sub_check = Integer.toString(check);

                            // compare the checksum of the message to the checksum attached
                            if(sub.equals(sub_check)) checksum = true;
                            
                            if(!r.contains(s)) {  
                                r.add(s);
                                new_mes++;
                            }
                            
                        }

                        bound++;
                        //if you have reached an end-line, sleep for one second then continue
                        if ((s = br.readLine()) == null)  Thread.sleep(1000);
                        
                    }  
                    
                    if(checksum) {
                        //send the message to the network layer
                        for(int j = new_mes; j < r.size(); j++) {
                             mes = r.get(j).toCharArray();
                            network_receive_from_datalink(mes, neighbors.get(i));
                        }

                    } else {
                            //if we don't have a full message, throw it out and increase the number of nacks that need to be sent
                            nack_num = num + num1;
                            nack_to_send++;
                    }
                }
            }
        }
    }

    public static void network_receive_from_datalink(char[] msg, int neighbor_id) throws IOException { 

        //check if the destination inside the message matches the current node
        boolean destination = false;
        char n = (char) (node_id + '0');
        for (char c : msg) {
                if (c == n)  destination = true;
        }

        //if destination matches, send it to the transport layer, otherwise send it back to the datalink layer w/ the next destination
        if (destination) {
                transport_receive_from_network(msg, msg.length, neighbor_id);
        } else {
            char hop = 0;
            char dest;
            dest = msg[4];
            //check each of our neighbors to see if they are or have the next destination as a neighbor
            for (int i = 0; i < neighbors.size();i++) {
                File myFile = new File("from" + neighbors.get(i) + "to" + dest + ".txt");
                boolean exists = myFile.exists();
                    
                if (neighbors.get(i) == dest || exists) {
                    
                    int h = neighbors.get(i);
                    hop = (char) (h + '0');
                    break;
                } else {
                    
                    //check the LSPs for each live node and see if they contain the destination as a neighbor
                    File f = new File("from" + neighbors.get(i) + "to" + node_id + ".txt");
                    String[] check = new String[50];
                    FileReader fr = new FileReader(f);
                    BufferedReader br = new BufferedReader(fr);
                    String str;
                    String input = "L" + neighbors.get(i);
                    String input_1 = "" + dest;
                    
                    int count = 0;
                    while ((str = br.readLine()) != null) {
                        
                        check = str.split(" ");
                        for (String word : check) {
                            if(word.contains(input) && word.contains(input_1)) count++;
                        }
                    }
                    
                    if (count != 0) {
                        int h = neighbors.get(i);
                        hop = (char) (h + '0');
                        break;
                    }
                }
            }
            
            //send the message back out
            datalink_receive_from_network(msg, hop);
        }
    }

    public static void network_receive_from_transport(char[] msg, int len, int des) throws IOException {

        int size = 15;
        int padding = 0;
        String length = String.format("%02d", len);
        String str = null;
        
        str = new String(msg);
        
        String dp = "D" + des + length + str;
        
        //if the data message isn't long enough, add padding
        if (dp.length() < size) {
            int new_len = dp.length();
            padding = size - new_len;

            for (int i = 0; i < padding; i++) {

                    dp += " ";
            }
        }
        
        //calculate the sum of all the ASCII characters in the message
        int checksum = 0;
        char[] dp_ch = dp.toCharArray();
        
        for (int i = 0; i < dp.length(); i++) {
            checksum += dp_ch[i];
        }
        
        //create the checksum and append it to the end of the message
        //also add the "XX" to the beginning
        checksum = checksum % 100;
        String cs = String.format("%02d", checksum);
        dp = "XX" + dp + cs;
        
        char[] dp_char = dp.toCharArray();
        
        //send message to datalink layer
        datalink_receive_from_network(dp_char, des);
        
    }

    public static void network_route() throws IOException { 

        int size = 15;
        String seq_num = String.format("%02d", net_clock);

        String n_list = "";

        //create a list of all our neighbors
        for (int i = 0; i < neighbors.size(); i++) {
            int n = neighbors.get(i);
            n_list += (char) (n + '0');
        }

        //put together Link State Packet
        String LSP = "L" + node_id + seq_num + n_list;

        //if the information provided is less than 15 bytes, pad the rest of the message
        if(LSP.length() < size) {
            int length = LSP.length();
            int padding = size - length;

            for (int i = 0; i < padding; i++) {

                    LSP += " ";
            }
        }
        
        //calculate the sum of all the ASCII characters in the message
        int checksum = 0;
        char[] lsp_ch = LSP.toCharArray();
        
        for (int i = 0; i < LSP.length(); i++) {
            checksum += lsp_ch[i];
        }
        
        //create the checksum and append it to the end of the message
        //also add the "XX" to the beginning
        checksum = checksum % 100;
        String cs = String.format("%02d", checksum);
        LSP = "XX" + LSP + cs;

        int count = 0;

        //send the LSP to all of the nodes neighbors
        while(count < neighbors.size()) {
            File myFile = new File("from" + node_id + "to" + neighbors.get(count)+ ".txt");
            try (BufferedWriter WriteFile = new BufferedWriter(new FileWriter(myFile,true))) {
                WriteFile.write(LSP);
                WriteFile.write("\n");
            }
            count++;
        }
        
        net_clock++;
    }

    public static void transport_send() throws IOException, InterruptedException {
        
        //creation of data messages for transport layer
        int size = 5;
        int trans_clock = 0;
        String source = Integer.toString(node_id);
        String dest = Integer.toString(dest_id);
        
        
        //if there is a message that needs to be sent, send it
        if (msg_flag) {
            
            msg_flag = false;
            if (message != null) {
                String text = message;
                
                ArrayList<String> substr = new ArrayList<String>();
                
                int mes_len = text.length();
                int sub_len = 0;
                
                if (mes_len % 5 != 0) {
                    sub_len = (mes_len / 5) + 1;
                    int pad = 5 - (mes_len % 5);
                    for (int i = 0; i < pad; i++) {
                        text += " ";
                    }
                } else {
                    sub_len = mes_len / 5;
                }

                //split the message into 5 byte sub strings
                for(int i = 0; i < sub_len; i++) {
                   substr.add(text.substring(0 + size * i, size * (i + 1)));
                }

                ArrayList<String>dp = new ArrayList<String>();

                //create the data message
                for (int i = 0; i < substr.size(); i++) {
                    String seq_num_dp = String.format("%02d", trans_clock);
                    dp.add("D" + source + dest + seq_num_dp + substr.get(i));
                    
                    trans_clock++;
                }

                long end = System.currentTimeMillis();
                long elapsed = end - start;
                if (elapsed >= start_time) {
                    //send message to network layer
                    for(int i = 0; i < substr.size(); i++) {

                        char[] dp_ch = dp.get(i).toCharArray();
                        network_receive_from_transport(dp_ch, dp.get(i).length(), dest_id);
                    }
                } else {

                    //sleep for 1 sec then try to send the message to the network layer
                    while(elapsed < start_time) {
                        Thread.sleep(1000);
                        end = System.currentTimeMillis();
                        elapsed = start - end;
                        if(elapsed >= start_time) {
                            for(int i = 0; i < substr.size(); i++) {

                                char[] dp_ch = dp.get(i).toCharArray();
                                network_receive_from_transport(dp_ch, dp.get(i).length(), dest_id);
                            }

                            break;
                        }
                    }
                }
            }
        }
        
        
        //while there are nacks that need to be sent, get the sequence number and send them out
        String holder = null;
        holder = "N" + source + dest + nack_num;
        char[] nack = null;
        nack = holder.toCharArray();
        while (nack_to_send > 0) {
            network_receive_from_transport(nack, nack.length, dest_id);
            nack_to_send--;
        }
    }

    public static void transport_receive_from_network(char[] msg, int len, int source) throws IOException {
        
        if (msg[2] != 'L' || msg[2] != 'N') {
             //store all the messages we get and the source of those messages
            stored_messages[msg_counter] = new String(msg);
            sources[msg_counter] = source;
            msg_counter++;
        }
       
    }

    public static void transport_output_all_received() throws IOException { 
        
        //create a file to output all the messages we've received
        File myFile = new File("thenode" + node_id + "received.txt");
        try {
            if (myFile.createNewFile()) {
                System.out.println("File created: " + myFile.getName());
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
          System.out.println("An error occurred.");
          e.printStackTrace();
        }
        
        //take all the messages and right them to the file we just created
        try (BufferedWriter WriteFile = new BufferedWriter(new FileWriter(myFile,true))) {
            for (int i = 0; i < msg_counter;i++) {
                WriteFile.write("From " + sources[i] + " received: " + stored_messages[i]);
                WriteFile.write("\n");
            }
            
        }
        
    }


    public static void main(String[] args) throws InterruptedException, IOException {
        
        //initialize variables from command line
        node_id = Integer.parseInt(args[0]);
        life = Integer.parseInt(args[1]);
        dest_id = Integer.parseInt(args[2]);
        
        //check to see if there is a message passed in the arugments or not.
        //If there is, we need to store the message in its own variable
        //Otherwise bypass that variable and move on to the neighbor list
        if(args[3].length() > 2) {
            msg_flag = true;
            message = args[3];
            start_time = Integer.parseInt(args[4]);

            for (int i = args.length - 1; i >= 5; i--) {
                neighbors.add(Integer.parseInt(args[i]));
            }
        } else {
            for (int i = args.length - 1; i >= 3; i--) {
                neighbors.add(Integer.parseInt(args[i]));
            }
        }
        
        
        //create files for messages from this node to each neighbor
        for (int i = 0; i < neighbors.size(); i++) {
            File myFile = new File("from" + node_id + "to" + neighbors.get(i) + ".txt");
            try {
                if (myFile.createNewFile()) {
                    System.out.println("File created: " + myFile.getName());
                } else {
                    System.out.println("File: " + myFile.getName() + " already exists");
                }
            } catch (IOException e) {
              System.out.println("An error occurred.");
              e.printStackTrace();
            }
        }
        
         Timer timer = new Timer();
            int begin = 0;
            int timeInterval = 10000;
            
            //every 10 secs send out a LSP
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        Node.network_route();
                    } catch (IOException ex) {
                        System.out.println(ex);
                    }
                }
            }, begin, timeInterval);

        //as long as the process is alive, send/receive messages
        for(int i = 0; i < life; i++) {  

            datalink_receive_from_channel();
            transport_send();

               
            Thread.sleep(1000);
            
        }
        
        //once the program's life is up, right all the messages received to a file
        transport_output_all_received();
        System.out.println("Node " + node_id + " terminated");
        System.exit(0);
    }
}
