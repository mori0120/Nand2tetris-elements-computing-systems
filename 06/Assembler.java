import java.util.HashMap;
import java.util.ArrayList;
import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class Assembler{
    public static void main(String[] args){
        try{
            if(args.length!=1) throw new IllegalArgumentException("File path args should be only one.");
            if(args[0].indexOf(".asm")<0) throw new IllegalArgumentException("File type is invalid. Only asm file is valid.");
            Path path = Paths.get(args[0].replace(".asm",".hack"));
            BufferedWriter file = Files.newBufferedWriter(path);
            Parser parser = new Parser(args[0]);
            SymbolTable table = new SymbolTable();
            int address = 16;
            int curr = -1;
            while(parser.hasMoreCommands()){
                parser.advance();
                String commandType = parser.commandType();
                if(commandType.equals("L_COMMAND")) table.addEntry(parser.symbol(), curr+1);
                else curr++;
            }
            parser.backToStart();
            while(parser.hasMoreCommands()){
                parser.advance();
                String str = "";
                String commandType = parser.commandType();
                if(commandType.equals("L_COMMAND")) continue;
                if(commandType.equals("A_COMMAND")){
                    String symbol = parser.symbol();
                    if(symbol.matches("[0-9]*")) str = Integer.toBinaryString(Integer.valueOf(symbol));
                    else{
                        if(!table.contains(symbol)) table.addEntry(symbol, address++);
                        str = Integer.toBinaryString(table.getAddress(symbol));
                    }
                    if(str.length()>16) throw new IndexOutOfBoundsException("Given number is out of range of memory address(0-32767).");
                    while(str.length()<16){
                        str = "0"+str;
                    }
                }else{
                    str = "111" + Code.comp(parser.comp()) + Code.dest(parser.dest()) + Code.jump(parser.jump());
                }
                file.write(str);
                file.newLine();
            }
            file.close();
        } catch(IOException|IllegalArgumentException|IndexOutOfBoundsException ex){
            ex.printStackTrace();
        }
    }
}

class Parser{
    private String command = "";
    private ArrayList<String> content = new ArrayList<>();
    private int index = -1;
        
    public Parser(String inputPath){
        try {
            Path path = Paths.get(inputPath);
            String[] strArr = Files.readString(path).split("\n");
            for(int i=0; i<strArr.length; i++){
                // 空文字とコメントを削除
                // なぜか文字列に空白や\"が入って(?)数値変換時のNumberFormatExceptionや次の空行スキップが上手く動作しないためtrim()
                strArr[i]=strArr[i].trim().replaceAll("\s", "").replaceAll("//.*", "");
                if(strArr[i].equals("")) continue;
                this.content.add(strArr[i]);
            }
        } catch(IOException|IllegalArgumentException ex) {
            ex.printStackTrace();
        }
    }

    public void backToStart(){
        this.index = -1;
        this.command = "";
    }

    public boolean hasMoreCommands(){
        return this.index+1 < this.content.size();
    }

    public void advance(){
        if(this.hasMoreCommands()){
            this.index++;
            this.command = this.content.get(this.index);
        }
    }

    public String commandType(){
        if(this.command.charAt(0)=='@') return "A_COMMAND";
        if(this.command.charAt(0)=='(' && this.command.charAt(this.command.length()-1)==')') return "L_COMMAND";
        return "C_COMMAND";
    }

    public String symbol(){
        if(this.commandType().equals("A_COMMAND")){
            return this.command.substring(1);
        }else if(this.commandType().equals("L_COMMAND")){
            return this.command.substring(1, this.command.length()-1);
        }
        return "";
    }

    public String dest(){
        if(this.commandType().equals("C_COMMAND") && this.command.indexOf('=')>=0){
            return this.command.substring(0, this.command.indexOf('='));
        }

        return "";
    }

    public String comp(){
        if(this.commandType().equals("C_COMMAND")){
            int indexEqual = this.command.indexOf('=');
            int indexSemicolon = this.command.indexOf(';');
            String c = new String(this.command);
            if(indexEqual>=0) c=c.substring(indexEqual+1);
            if(indexSemicolon>=0) c=c.substring(0,indexSemicolon);
            return c;
        }
        return "";
    }

    public String jump(){
        if(this.commandType().equals("C_COMMAND") && this.command.indexOf(';')>=0){
            return this.command.substring(this.command.indexOf(';')+1);
        }
        return "";
    }
}

class Code{
    private static HashMap<String, String> destMap = new HashMap<>(){
        {
            put("null", "000");
            put("M", "001");
            put("D", "010");
            put("MD", "011");
            put("A", "100");
            put("AM", "101");
            put("AD", "110");
            put("AMD", "111");
        }
    };

    private static HashMap<String, String> compMap = new HashMap<>(){
        {
            put("0", "0101010");
            put("1", "0111111");
            put("-1","0111010");
            put("D", "0001100");
            put("A", "0110000");
            put("M", "1110000");
            put("!D","0001101");
            put("!A","0110001");
            put("!M","1110001");
            put("-D","0001111");
            put("-A","0110011");
            put("-M","1110011");
            put("D+1","0011111");
            put("A+1","0110111");
            put("M+1","1110111");
            put("D-1","0001110");
            put("A-1","0110010");
            put("M-1","1110010");
            put("D+A","0000010");
            put("D+M","1000010");
            put("D-A","0010011");
            put("D-M","1010011");
            put("A-D","0000111");
            put("M-D","1000111");
            put("D&A","0000000");
            put("D&M","1000000");
            put("D|A","0010101");
            put("D|M","1010101");
        }
    };

    private static HashMap<String, String> jumpMap = new HashMap<>(){
        {
            put("null", "000");
            put("JGT", "001");
            put("JEQ", "010");
            put("JGE", "011");
            put("JLT", "100");
            put("JNE", "101");
            put("JLE", "110");
            put("JMP", "111");
        }
    };

    public static String dest(String dest){
        return Code.destMap.getOrDefault(dest, "000");
    }

    public static String comp(String comp){
        return Code.compMap.getOrDefault(comp, "0000000");
    }

    public static String jump(String jump){
        return Code.jumpMap.getOrDefault(jump, "000");
    }
}

class SymbolTable{
    private HashMap<String, Integer> table;
    private int address;

    public SymbolTable(){
        this.address = 16;
        this.table = new HashMap<String, Integer>(){
            {
                put("SP", 0);
                put("LCL", 1);
                put("ARG", 2);
                put("THIS", 3);
                put("THAT", 4);
                put("SCREEN", 16384);
                put("KBD", 24576);
            }
        };

        for(int i=0; i<=15; i++){
            this.table.put("R"+i, i);
        }
    }

    public void addEntry(String symbol, int address){
        this.table.put(symbol, address);
    }

    public boolean contains(String symbol){
        return this.table.containsKey(symbol);
    }

    public int getAddress(String symbol){
        return this.table.getOrDefault(symbol, -1);
    }
}