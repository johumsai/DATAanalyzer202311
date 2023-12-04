package com.servlet;

import com.data.DetailLogVo;
import com.data.MasterLogVo;
import com.data.ResultLogVo;
import com.mybatis.EsbDAO;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.util.WsTxidValueGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang.time.DateFormatUtils;
import org.codehaus.jackson.map.ObjectMapper;

@WebServlet(value={"/FileSend"})
public class UploadActionMultiPart
extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int BYTE = 1024;
    private static final int KILOBYTE = 1024;
    private static final int MEMORY_THRESHOLD = 0x100000;
    private static final long MAX_FILE_SIZE = 0x7FF00000L;
    private static final long MAX_REQUEST_SIZE = -1048576L;
    private String FILE_STORAGE = "";
    private EsbDAO dao = EsbDAO.getInstance();

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("Multipart Recevie Process...");
        long startTime = System.currentTimeMillis();
        boolean valid = false;
        String tx_id = null;
        String if_id = null;
        String tranLogUseYn = "";
        String companyDirectory = "";
        String rslt_year = "";
        String backendURL = "";
        String initTime = DateFormatUtils.format((long)startTime, (String)"yyyyMMddHHmmss");
        String apikey = "";
        Object errMsg = null;
        Map route_info = new HashMap();
        HashMap<String, String> zipFileNames = new HashMap<String, String>();
        HashMap<String, Object> requestObj = new HashMap<String, Object>();
        ArrayList file_list = new ArrayList();
        Properties prop = new Properties();
        try {
            prop.load(((Object)((Object)this)).getClass().getClassLoader().getResourceAsStream("api.properties"));
            tranLogUseYn = prop.getProperty("tranLogUseYn");
            this.FILE_STORAGE = prop.getProperty("fileStorage");
            backendURL = prop.getProperty("backendURL");
            apikey = String.valueOf(prop.getProperty("apikey")) + initTime;
            System.out.println("[properties] tranLogUseYn => " + prop.getProperty("tranLogUseYn"));
            System.out.println("[properties] fileStorage => " + prop.getProperty("fileStorage"));
            System.out.println("[properties] BackendURL => " + prop.getProperty("BackendURL"));
            System.out.println("[properties] apikey with current time => " + prop.getProperty("apikey") + initTime);
        }
        catch (FileNotFoundException fe) {
            fe.printStackTrace();
        }
        System.out.println("content-type : " + request.getContentType());
        if (ServletFileUpload.isMultipartContent((HttpServletRequest)request)) {
            DiskFileItemFactory factory = new DiskFileItemFactory();
            factory.setSizeThreshold(0x100000);
            factory.setRepository(new File(this.FILE_STORAGE));
            ServletFileUpload upload = new ServletFileUpload((FileItemFactory)factory);
            upload.setFileSizeMax(0x7FF00000L);
            upload.setSizeMax(-1048576L);
            upload.setHeaderEncoding("utf-8");
            String uploadPath = this.FILE_STORAGE;
            System.out.println("uploadPath => " + uploadPath);
            File uploadStorage = new File(uploadPath);
            if (!uploadStorage.isDirectory()) {
                uploadStorage.mkdir();
            }
            try {
                List<FileItem> formItems = upload.parseRequest(request);
                System.out.println("!! formItems => " + formItems.toString());
                if (formItems != null && formItems.size() > 0) {
                    for (FileItem item : formItems) {
                        if (!item.isFormField()) continue;
                        System.out.println("form data name => " + item.getFieldName() + ", data => " + item.getString());
                        if (item.getFieldName().equals("interfaceId")) {
                            if_id = item.getString();
                            tx_id = WsTxidValueGenerator.create((String)if_id, (long)System.currentTimeMillis());
                            System.out.println("item Content Type => " + item.getContentType());
                            if (item.getContentType() == null) {
                                if_id = new String(if_id.getBytes("8859_1"), "utf-8");
                            }
                            if (tranLogUseYn.equals("Y")) {
                                if (this.dao.getRouteInfo(if_id) != null) {
                                    route_info = this.dao.getRouteInfo(if_id);
                                    System.out.println("RouteInfo : " + route_info);
                                    valid = true;
                                    this.insertMasterLog(tx_id, if_id);
                                    this.insertDetailLog(tx_id, if_id, (String)route_info.get("SND_CD"), (String)route_info.get("RCV_CD"));
                                } else {
                                    System.out.println("Invalid Interface ID!!");
                                    request.setAttribute("result", (Object)"Error : Invalid Interface ID!!");
                                    this.getServletContext().getRequestDispatcher("/fileResult.jsp").forward((ServletRequest)request, (ServletResponse)response);
                                }
                            } else {
                                valid = true;
                            }
                        }
                        if (item.getFieldName().equals("companyCd")) {
                            companyDirectory = item.getString();
                            File makeCompanyDir = new File(String.valueOf(uploadPath) + File.separator + companyDirectory);
                            if (!makeCompanyDir.isDirectory()) {
                                makeCompanyDir.mkdir();
                            }
                        }
                        if (!item.getFieldName().equals("rslt_year")) continue;
                        rslt_year = item.getString();
                    }
                    if (valid) {
                        ArrayList<String> fileNameList = new ArrayList<String>();
                        int k = 0;
                        for (FileItem item : formItems) {
                            if (item.isFormField()) continue;
                            System.out.println("multipart data file name => " + item.getName());
                            String fileName = new File(item.getName()).getName();
                            String filePath = String.valueOf(uploadPath) + File.separator + companyDirectory + File.separator + fileName;
                            File storeFile = new File(filePath);
                            item.write(storeFile);
                            String[] tempFileName = fileName.split("\\.");
                            String tempNum = "0";
                            if (fileNameList.contains(tempFileName[1])) continue;
                            fileNameList.add(tempFileName[1]);
                            if ((k + 1) / 10 == 0) {
                                tempNum = "00";
                            }
                            HashMap<String, Object> file = new HashMap<String, Object>();
                            file.put("rslt_prufdoc_se", String.valueOf(tempNum) + (k + 1));
                            file.put("atchmnfl_path", String.valueOf(uploadPath) + File.separator + companyDirectory);
                            file.put("atchmnfl_nm", String.valueOf(tempFileName[0]) + "." + tempFileName[1]);
                            file.put("atchmnfl_size", ""+item.getSize());
                            file.put("atchmnfl_extsn_nm", "." + tempFileName[2]);
                            file_list.add(file);
                            ++k;
                        }
                        System.out.println("zipFileNames => " + fileNameList);
                        int i = 1;
                        while (i < fileNameList.size() + 1) {
                            String num = "0";
                            num = i < 10 ? String.valueOf(num) + i : String.valueOf(i);
                            zipFileNames.put(num, String.valueOf(uploadPath) + File.separator + companyDirectory + File.separator + (String)fileNameList.get(i - 1));
                            ++i;
                        }
                        TreeMap sortedMap = new TreeMap(zipFileNames);
                        System.out.println("zipFileNames => " + sortedMap);
                        request.setAttribute("result", (Object)"업로드가 완료되었습니다.");
                    }
                }
            }
            catch (Exception ex) {
                request.setAttribute("result", (Object)("Error : " + ex.getMessage()));
                if (tranLogUseYn.equals("Y")) {
                    this.insertResultLog(tx_id, if_id, "F", 1, ex.getMessage().getBytes());
                }
                this.getServletContext().getRequestDispatcher("/fileResult.jsp").forward((ServletRequest)request, (ServletResponse)response);
                return;
            }
            long endTime = System.currentTimeMillis();
            long elapsed = endTime - startTime;
            System.out.println("소요시간 : " + (double)elapsed / 1000.0 + " s");
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.setHeader("Access-Control-Max-Age", "3600");
            response.setHeader("Access-Control-Allow-Headers", "Origin,Accept,X-Requested-With,Content-Type,Access-Control-Request-Method,Access-Control-Request-Headers,Authorization");
            requestObj.put("cmpy_id", companyDirectory);
            requestObj.put("rslt_year", rslt_year);
            requestObj.put("file_list", file_list);
            OkHttpClient client = new OkHttpClient();
            MediaType JSON = MediaType.parse((String)"application/json; charset=utf-8");
            RequestBody body = RequestBody.create((MediaType)JSON, (byte[])new ObjectMapper().writeValueAsBytes(requestObj));
            Request req = new Request.Builder().url(backendURL).post(body).addHeader("apikey", apikey+initTime).build();
            Response res = null;
            String resStr = "";
            try {
                res = client.newCall(req).execute();
                resStr = res.body().string();
                System.out.println(resStr);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            if (tranLogUseYn.equals("Y")) {
                if (res.code() != 200) {
                    this.insertResultLog(tx_id, if_id, "B", 1, resStr.getBytes());
                } else {
                    this.insertResultLog(tx_id, if_id, "S", 0, "".getBytes());
                }
            }
            System.out.println("response code => " + res.code());
            request.setAttribute("elapsedTime", (Object)(String.valueOf((double)elapsed / 1000.0) + " s"));
            this.getServletContext().getRequestDispatcher("/fileResult.jsp").forward((ServletRequest)request, (ServletResponse)response);
        }
    }

    public int insertMasterLog(String tx_id, String if_id) {
        MasterLogVo param = new MasterLogVo();
        param.setTx_id(tx_id);
        param.setIf_id(if_id);
        param.setMsg_cnt(1);
        param.setMsg_create_dt(DateFormatUtils.format((long)System.currentTimeMillis(), (String)"yyyyMMddHHmmssSSS"));
        param.setHub_rcv_dt(DateFormatUtils.format((long)System.currentTimeMillis(), (String)"yyyyMMddHHmmssSSS"));
        param.setMsg_type("N");
        param.setMsg_size(0);
        param.setEsb_host("EcoAS");
        return this.dao.insertMaster(param);
    }

    public int insertDetailLog(String tx_id, String if_id, String snd_cd, String rcv_cd) {
        DetailLogVo param = new DetailLogVo();
        param.setTx_id(tx_id);
        param.setDest_id("EcoAS");
        param.setIf_id(if_id);
        param.setSnd_cd(snd_cd);
        param.setRcv_cd(rcv_cd);
        param.setSnd_cnt(1);
        param.setMsg_create_dt(DateFormatUtils.format((long)System.currentTimeMillis(), (String)"yyyyMMddHHmmssSSS"));
        param.setHub_cvt_dt(DateFormatUtils.format((long)System.currentTimeMillis(), (String)"yyyyMMddHHmmssSSS"));
        param.setSnd_ad_inst_nm("");
        param.setRcv_ad_inst_nm("");
        param.setRcv_qu_nm("");
        param.setSnd_qu_nm("");
        param.setMsg_size(0);
        param.setSnd_msg("");
        return this.dao.insertDetail(param);
    }

    public int insertResultLog(String tx_id, String if_id, String status_cd, int err_cnt, byte[] errMsg) {
        ResultLogVo param = new ResultLogVo();
        param.setTx_id(tx_id);
        param.setDest_id("EcoAS");
        param.setStatus_cd(status_cd);
        param.setMsg_rcv_dt(DateFormatUtils.format((long)System.currentTimeMillis(), (String)"yyyyMMddHHmmssSSS"));
        param.setErr_cnt(err_cnt);
        param.setMsg_size(0);
        param.setRetry_cnt(0);
        param.setErr_msg(errMsg);
        return this.dao.insertResult(param);
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        this.setAccessControlHeaders(response);
        response.setStatus(200);
    }

    private void setAccessControlHeaders(HttpServletResponse resp) {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, PUT, POST, DELETE, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type, Access-Control-Allow-Headers, Authorization, X-Requested-With");
    }
}