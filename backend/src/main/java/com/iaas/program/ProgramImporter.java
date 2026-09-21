package com.iaas.program;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.common.BizException;
import com.iaas.course.entity.Course;
import com.iaas.course.mapper.CourseMapper;
import com.iaas.governance.AuditService;
import com.iaas.importer.ImportDtos;
import com.iaas.importer.SheetReader;
import com.iaas.program.entity.Program;
import com.iaas.program.entity.ProgramCourse;
import com.iaas.program.entity.ProgramModule;
import com.iaas.program.mapper.ProgramCourseMapper;
import com.iaas.program.mapper.ProgramMapper;
import com.iaas.program.mapper.ProgramModuleMapper;
import com.iaas.system.entity.Major;
import com.iaas.system.mapper.MajorMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 培养方案导入。
 *
 * <p>文件长这样：第 1 张表是"方案总览"（专业名称、学制、毕业最低学分，
 * 以及"毕业最低学分结构"——每个模块要求多少学分），后面每张表是一个模块的课程明细
 * （课程名、考核方式、学分、学时、各学期周学时、方向分组）。
 *
 * <p>这里做两件教务平时手工要核对的事：
 * <ol>
 *   <li><b>模块对账</b>：每张课程表的学分合计与总览里的结构表对不上时指出来。
 *       注意有些模块是"二选一方向"，合计天然大于要求学分，所以对账结果是提示而不是阻断。</li>
 *   <li><b>名称对齐课程库</b>：方案里没有课程代码，只能按名称对齐。
 *       对得上的记下 courseId，对不上的留空——毕业审核时按名称再对一次。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ProgramImporter {

    public static final String TYPE_PROGRAM = "program";

    /** 总览里"专业名称（专业代码 080901，学科性质：工学）"这种写法要拆开。 */
    private static final Pattern MAJOR_CODE = Pattern.compile("专业代码\\s*([0-9A-Za-z]+)");
    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern TERM_COL = Pattern.compile("学期(\\d)");

    private final SheetReader reader;
    private final ProgramMapper programMapper;
    private final ProgramModuleMapper moduleMapper;
    private final ProgramCourseMapper courseMapper;
    private final MajorMapper majorMapper;
    private final CourseMapper libraryCourseMapper;
    private final AuditService auditService;

    /** 解析结果：一个方案 + 若干模块与课程 + 报告行。 */
    private record Parsed(Program program, List<ProgramModule> modules,
                          Map<String, List<ProgramCourse>> coursesByModule,
                          List<ImportDtos.RowResult> rows, List<String> errors) {
    }

    public ImportDtos.Report importFile(MultipartFile file, boolean commit) {
        String name = file.getOriginalFilename() == null ? "(未命名文件)" : file.getOriginalFilename();
        try {
            return importBytes(name, file.getBytes(), commit);
        } catch (Exception e) {
            throw new BizException("文件读取失败：" + e.getMessage());
        }
    }

    /** 按字节导入，供启动时的自动导入复用。 */
    public ImportDtos.Report importBytes(String fileName, byte[] bytes, boolean commit) {
        Parsed parsed = parse(fileName, bytes);
        long failed = parsed.rows().stream().filter(r -> !r.ok()).count();
        boolean committed = false;
        if (commit) {
            if (!parsed.errors().isEmpty()) {
                return report(fileName, parsed, false);
            }
            save(parsed);
            committed = true;
            auditService.ingest("导入培养方案：" + parsed.program().getTitle()
                    + "，模块 " + parsed.modules().size()
                    + " 个，计划课程 " + parsed.coursesByModule().values().stream()
                    .mapToInt(List::size).sum() + " 门", 0);
        }
        return report(fileName, parsed, committed);
    }

    private ImportDtos.Report report(String fileName, Parsed parsed, boolean committed) {
        long failed = parsed.rows().stream().filter(r -> !r.ok()).count();
        return new ImportDtos.Report(TYPE_PROGRAM, "培养方案",
                fileName,
                // 总行数按报告行算（总览 + 学分结构 + 每张课程表各一行），不是模块数：
                // 否则 ok + failed 与 total 对不上，看报告的人会犯迷糊
                parsed.rows().size(), (int) (parsed.rows().size() - failed), (int) failed,
                committed, parsed.rows(), parsed.errors());
    }

    // ------------------------------------------------------------------
    // 解析
    // ------------------------------------------------------------------

    private Parsed parse(String fileName, byte[] bytes) {
        List<SheetReader.NamedTable> tables = reader.readAll(fileName, bytes);
        SheetReader.NamedTable overview = tables.stream()
                .filter(t -> t.name().contains("总览") || t.name().contains("方案"))
                .findFirst().orElse(tables.get(0));
        List<SheetReader.NamedTable> moduleSheets = tables.stream()
                .filter(t -> !Objects.equals(t.name(), overview.name()))
                .toList();

        List<ImportDtos.RowResult> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Program program = new Program();
        program.setTitle(firstText(overview));
        program.setStatus("现行");
        Map<String, BigDecimal> requiredByCategory = new LinkedHashMap<>();
        List<ProgramModule> modules = new ArrayList<>();
        readOverview(overview, program, modules, requiredByCategory, errors);

        Map<String, List<ProgramCourse>> byModule = new LinkedHashMap<>();
        Map<String, Course> library = libraryByName();
        int line = 1;
        // 总览也占一行：把"学分结构里有哪几个模块、哪些模块没有课程表"交代清楚，
        // 教务看报告时不用自己拿两份东西对照
        rows.add(new ImportDtos.RowResult(line++, "方案总览",
                errors.isEmpty(), "毕业最低 " + strip(program.getMinCredit())
                        + " 学分；学分结构 " + modules.size() + " 个模块"));

        Map<String, String> sheetToCategory = new LinkedHashMap<>();
        List<String> noSheet = new ArrayList<>();
        for (ProgramModule m : modules) {
            String hit = bestMatch(m.getCategory(), moduleSheets.stream()
                    .map(SheetReader.NamedTable::name).toList());
            if (hit == null) {
                noSheet.add(m.getCategory());
            } else {
                sheetToCategory.put(hit, m.getCategory());
            }
        }
        if (!noSheet.isEmpty()) {
            rows.add(new ImportDtos.RowResult(line++, "学分结构",
                    true, "这些模块没有对应的课程表，通常由学院统一开设或另行安排："
                            + String.join("、", noSheet)));
        }

        for (SheetReader.NamedTable sheet : moduleSheets) {
            line++;
            try {
                List<ProgramCourse> courses = readCourseSheet(sheet);
                if (courses.isEmpty()) {
                    rows.add(new ImportDtos.RowResult(line, sheet.name(), true, "没有课程行，跳过"));
                    continue;
                }
                BigDecimal sum = courses.stream().map(ProgramCourse::getCredit)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                String category = sheetToCategory.get(sheet.name());
                // 模块名统一存"学分结构里的类别名"：审核是按类别对账的，
                // 存工作表名会让"学科基础必修课"和"学科（专业）基础必修课"对不上
                if (category != null) {
                    courses.forEach(c -> c.setModule(category));
                }
                BigDecimal required = category == null ? null : requiredByCategory.get(category);
                String message = "共 " + courses.size() + " 门，合计 " + strip(sum) + " 学分";
                if (required != null && required.compareTo(sum) != 0) {
                    // 不阻断：方案里常见"两个方向二选一"，合计天然大于要求学分
                    message += "（学分结构表要求 " + strip(required)
                            + "：差额通常来自二选一方向、或只要求修满其中若干门，"
                            + "毕业审核按要求学分计算）";
                } else if (required != null) {
                    message += "，与学分结构表一致";
                } else {
                    message += "（不在毕业要求结构表里，如辅修模块，仅供参考）";
                }
                for (ProgramCourse c : courses) {
                    Course hit = library.get(ProgramService.normalize(c.getCourseName()));
                    if (hit != null) {
                        c.setCourseId(hit.getId());
                    }
                }
                byModule.put(sheet.name(), courses);
                rows.add(new ImportDtos.RowResult(line, sheet.name(), true, message));
            } catch (Exception e) {
                String msg = e instanceof BizException ? e.getMessage() : "这张表读不出来";
                rows.add(new ImportDtos.RowResult(line, sheet.name(), false, msg));
                if (errors.size() < 30) {
                    errors.add("工作表「" + sheet.name() + "」：" + msg);
                }
            }
        }
        return new Parsed(program, modules, byModule, rows, errors);
    }

    /**
     * 把学分结构里的类别名和工作表名对上。
     *
     * <p>两边写法不一样是常态："学科（专业）基础必修课" 对 "学科基础必修课"，
     * "专业技术技能型实践环节" 对 "实践环节"。所以用最长公共子串占比来判断相似度，
     * 取最像的那一个；太不像就认为没有对应课程表。
     */
    private static String bestMatch(String category, List<String> sheetNames) {
        String target = normalizeCategory(category);
        String best = null;
        double bestScore = 0;
        for (String sheet : sheetNames) {
            String s = normalizeCategory(sheet);
            if (s.isEmpty() || target.isEmpty()) {
                continue;
            }
            double score = (double) longestCommonSubstring(target, s) / Math.min(target.length(), s.length());
            if (score > bestScore) {
                bestScore = score;
                best = sheet;
            }
        }
        return bestScore >= 0.6 ? best : null;
    }

    private static String normalizeCategory(String raw) {
        return raw.replaceAll("\\s+", "")
                .replace("（", "").replace("）", "")
                .replace("(", "").replace(")", "")
                .replace("应修", "");
    }

    private static int longestCommonSubstring(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        int best = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    best = Math.max(best, dp[i][j]);
                }
            }
        }
        return best;
    }

    private void readOverview(SheetReader.NamedTable table, Program program,
                              List<ProgramModule> modules,
                              Map<String, BigDecimal> requiredByCategory,
                              List<String> errors) {
        // 总览表的第 1 行是文档标题，被 SheetReader 当成了表头，
        // 这里补回去，否则标题会取到"数据来源"那一行
        List<List<String>> rows = new ArrayList<>();
        rows.add(table.table().header());
        rows.addAll(table.table().rows());
        boolean inStructure = false;
        int sort = 0;
        for (List<String> row : rows) {
            String first = cell(row, 0);
            String second = firstNonEmpty(row, 1);
            if (first.startsWith("数据来源")) {
                program.setSourceNote(trimTo(first, 300));
                continue;
            }
            if (program.getTitle() == null || program.getTitle().isBlank()) {
                program.setTitle(first);
            }
            if (first.contains("专业名称") && !second.isBlank()) {
                String name = second;
                Matcher m = MAJOR_CODE.matcher(second);
                if (m.find()) {
                    name = second.substring(0, m.start()).trim();
                }
                program.setMajorName(name.replace("（", "(").split("\\(")[0].trim());
            }
            if (first.contains("标准学制") && !second.isBlank()) {
                program.setDuration(second);
            }
            if (first.contains("授予学位") && !second.isBlank()) {
                program.setDegree(second);
            }
            if (first.contains("毕业最低学分") && !second.isBlank()) {
                BigDecimal credit = number(second);
                if (credit != null) {
                    program.setMinCredit(credit);
                }
            }
            if (first.contains("学分结构")) {
                inStructure = true;
                continue;
            }
            if (!inStructure) {
                continue;
            }
            if (first.contains("课程类别")) {
                continue; // 表头
            }
            // "合  计"这种带空格的写法要先去空格再判，否则会被当成一个模块
            String compact = first.replaceAll("\\s+", "");
            if (compact.contains("合计") || compact.contains("小计")) {
                continue; // 汇总行不是模块
            }
            BigDecimal credit = number(cell(row, 2));
            if (credit == null || first.isBlank()) {
                // 结构表结束（后面是"应选修最低…"这类说明）
                if (first.startsWith("应选修") || first.startsWith("通识教育选修课")) {
                    program.setSourceNote(trimTo(
                            (program.getSourceNote() == null ? "" : program.getSourceNote() + "；")
                                    + first, 300));
                }
                continue;
            }
            ProgramModule m = new ProgramModule();
            m.setCategory(first);
            m.setHoursText(cell(row, 1));
            m.setCredit(credit);
            m.setRatio(number(cell(row, 3)));
            m.setSortNo(sort++);
            modules.add(m);
            requiredByCategory.put(first, credit);
        }
        if (program.getMinCredit() == null) {
            errors.add("总览里没读到「毕业最低学分」，这份文件可能不是培养方案");
        }
        if (modules.isEmpty()) {
            errors.add("总览里没读到「毕业最低学分结构」，无法按模块算缺口");
        }
    }

    /**
     * 读一张课程表。
     *
     * <p>两类行要区分：课程行（有学分）与分组标题行（如"A、网络技术与安全方向（二选一）"）。
     * 分组标题后面跟着的课程属于这个方向，记下来供审核时说明。
     */
    private List<ProgramCourse> readCourseSheet(SheetReader.NamedTable sheet) {
        List<List<String>> rows = sheet.table().rows();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = sheet.table().header();
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.put(header.get(i).trim(), i);
        }
        boolean practice = index.containsKey("实践环节");
        Integer nameCol = practice ? index.get("实践环节") : index.get("课程名称");
        if (nameCol == null) {
            throw new BizException("表头里没有课程名称列");
        }
        Integer creditCol = index.get("学分");
        Integer assessCol = index.get("考核方式");
        Integer hoursCol = practice ? index.get("学时(时)") : index.get("总学时");
        Integer labCol = index.get("实验");
        Integer pcCol = index.get("上机");
        Integer typeCol = index.get("类型");
        Integer noteCol = index.get("备注");
        List<Integer> termCols = new ArrayList<>();
        List<Integer> termNos = new ArrayList<>();
        for (Map.Entry<String, Integer> e : index.entrySet()) {
            Matcher m = TERM_COL.matcher(e.getKey());
            if (m.find()) {
                termNos.add(Integer.parseInt(m.group(1)));
                termCols.add(e.getValue());
            }
        }

        String required = sheet.name().contains("必修") ? "必修"
                : sheet.name().contains("选修") ? "选修" : null;
        List<ProgramCourse> out = new ArrayList<>();
        String group = null;
        for (List<String> row : rows) {
            String name = string(row, nameCol);
            String creditText = creditCol == null ? "" : string(row, creditCol);
            if (name.isBlank()) {
                continue;
            }
            if (creditText.isBlank()) {
                // 没有学分：这是分组标题行，不是课程
                group = name;
                continue;
            }
            BigDecimal credit = number(creditText);
            if (credit == null) {
                throw new BizException("「" + name + "」的学分不是数字：" + creditText);
            }
            ProgramCourse c = new ProgramCourse();
            c.setModule(sheet.name());
            c.setGroupName(group);
            c.setCourseName(name);
            c.setCourseType(practice ? "实践" : "理论");
            c.setAssessType(assessCol == null ? null : nullIfBlank(string(row, assessCol)));
            c.setCredit(credit);
            c.setTotalHours(intOrNull(hoursCol == null ? "" : string(row, hoursCol)));
            c.setLabHours(intOrNull(labCol == null ? "" : string(row, labCol)));
            c.setComputerHours(intOrNull(pcCol == null ? "" : string(row, pcCol)));
            c.setNote(noteCol == null ? null : nullIfBlank(string(row, noteCol)));
            c.setRequired(typeCol != null && !string(row, typeCol).isBlank()
                    ? string(row, typeCol) : required);
            for (int i = 0; i < termCols.size(); i++) {
                String week = string(row, termCols.get(i));
                if (!week.isBlank()) {
                    c.setTermNo(termNos.get(i));
                    c.setWeekHours(intOrNull(week));
                    break;
                }
            }
            out.add(c);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 落库
    // ------------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public void save(Parsed parsed) {
        Program p = parsed.program();
        Major major = majorMapper.selectOne(Wrappers.<Major>lambdaQuery()
                .eq(Major::getName, p.getMajorName()).last("limit 1"));
        if (major != null) {
            p.setMajorId(major.getId());
            // 同一专业已有现行方案时置为停用：方案不是删掉，往届生还要按当年的审
            List<Program> olds = programMapper.selectList(Wrappers.<Program>lambdaQuery()
                    .eq(Program::getMajorId, major.getId())
                    .eq(Program::getStatus, "现行"));
            for (Program old : olds) {
                old.setStatus("停用");
                programMapper.updateById(old);
            }
        }
        p.setImportedAt(java.time.LocalDateTime.now());
        programMapper.insert(p);

        for (ProgramModule m : parsed.modules()) {
            m.setProgramId(p.getId());
            moduleMapper.insert(m);
        }
        for (List<ProgramCourse> list : parsed.coursesByModule().values()) {
            for (ProgramCourse c : list) {
                c.setProgramId(p.getId());
                courseMapper.insert(c);
            }
        }
    }

    /** 课程库按归一化名称索引，用于把计划课程与课程库对上。 */
    private Map<String, Course> libraryByName() {
        return libraryCourseMapper.selectList(null).stream()
                .collect(Collectors.toMap(c -> ProgramService.normalize(c.getName()),
                        Function.identity(), (a, b) -> a));
    }

    // ------------------------------------------------------------------

    private static String firstText(SheetReader.NamedTable table) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(table.table().header());
        rows.addAll(table.table().rows());
        for (List<String> row : rows) {
            for (String c : row) {
                if (!c.isBlank()) {
                    return c;
                }
            }
        }
        return "培养方案";
    }

    private static String cell(List<String> row, int i) {
        return i < row.size() && row.get(i) != null ? row.get(i).trim() : "";
    }

    private static String string(List<String> row, Integer i) {
        return i == null ? "" : cell(row, i);
    }

    private static String firstNonEmpty(List<String> row, int from) {
        for (int i = from; i < row.size(); i++) {
            if (row.get(i) != null && !row.get(i).isBlank()) {
                return row.get(i).trim();
            }
        }
        return "";
    }

    /** 取第一个数字。学分列写"34"、学时列写"660 学时"都能取到。 */
    private static BigDecimal number(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = NUMBER.matcher(raw);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private static Integer intOrNull(String raw) {
        BigDecimal d = number(raw);
        return d == null ? null : d.intValue();
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String trimTo(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String strip(BigDecimal d) {
        return d.stripTrailingZeros().toPlainString();
    }
}
