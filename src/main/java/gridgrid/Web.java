/*
 * Copyright 2015 Yusuke Yamamoto
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gridgrid;

import lombok.Data;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

@RestController
public class Web {
    private Map<String, CodeView> map = new HashMap<>();

    long lastModified = -1L;

    @RequestMapping(value = "/*", produces = MediaType.TEXT_HTML_VALUE)
    public String get(Model model, HttpServletRequest request) throws IOException {
        File file = new File("./hogan.xlsx");
        load(file);
        CodeView codeView = map.get(request.getRequestURI());
        try {
            codeView.runCode();
            return codeView.getView();
        } catch (ScriptException e) {
            return e.getMessage();
        }
    }

    private synchronized void load(File file) throws IOException {
        if (file.lastModified() > lastModified) {
            map = new HashMap<>();
            InputStream is = new FileInputStream(file);
            Workbook book = new XSSFWorkbook(is);
            Sheet sheet = book.getSheetAt(0);
            int pathCelNum = -1;
            int scriptCellNum = -1;
            int viewCellNum = -1;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    if (row.getLastCellNum() >= 1 && pathCelNum == -1) {
                        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                            Cell cell = row.getCell(cellIndex);
                            if (cell != null) {
                                switch (cell.getStringCellValue()) {
                                    case "パス":
                                        pathCelNum = cellIndex;
                                        break;
                                    case "JavaScript":
                                        scriptCellNum = cellIndex;
                                        break;
                                    case "ビュー":
                                        viewCellNum = cellIndex;
                                        break;
                                }
                            }
                        }

                    }

                    if (pathCelNum != -1 && row.getCell(pathCelNum) != null
                        && row.getCell(scriptCellNum) != null
                        && row.getCell(viewCellNum) != null) {
                        Cell code = row.getCell(scriptCellNum);
                        String codeStr = code != null ? code.toString() : "";
                        Cell view = row.getCell(viewCellNum);
                        String viewStr = view != null ? view.toString() : "";
                        String path = row.getCell(pathCelNum).toString();
                        map.put(path, new CodeView(codeStr, viewStr));
                    }
                }
            }
            is.close();
            lastModified = file.lastModified();
        }
    }

    @Data
    class CodeView {
        ScriptEngineManager manager = new ScriptEngineManager();

        private String code;
        private String view;
        ScriptEngine engine;

        public CodeView(String code, String view) {
            this.code = code;
            this.view = view;
            engine = manager.getEngineByName("nashorn");
        }

        public void runCode() throws ScriptException {
            engine.eval(code);
        }
    }

}
