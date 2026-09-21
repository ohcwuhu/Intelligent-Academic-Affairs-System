package com.iaas.program;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.iaas.program.entity.Program;
import com.iaas.program.mapper.ProgramMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 启动时按需导入培养方案。
 *
 * <p>与知识库语料同样的处理方式：语料/方案是"资料"，放在仓库里，
 * 启动时发现库里没有就自动灌进去，演示环境不用先手工导一遍。
 * 已经有方案就跳过，不会覆盖教务后来导入的版本。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProgramInitializer implements ApplicationRunner {

    private final ProgramMapper programMapper;
    private final ProgramImporter importer;

    /** 默认相对后端工作目录（启动时的工作目录必须是 backend，README 里有说明）。 */
    @Value("${iaas.program.source-dir:../docs/import-samples/培养方案}")
    private String sourceDir;

    @Value("${iaas.program.auto-import:true}")
    private boolean autoImport;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoImport) {
            return;
        }
        Long existing = programMapper.selectCount(Wrappers.<Program>lambdaQuery());
        if (existing != null && existing > 0) {
            log.info("培养方案已存在 {} 份，跳过自动导入", existing);
            return;
        }
        Path dir = Path.of(sourceDir);
        if (!Files.isDirectory(dir)) {
            log.info("培养方案目录不存在（{}），跳过自动导入；可在「数据导入」里手工上传", dir);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> xlsx = files
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".xlsx") || n.endsWith(".xls");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            int ok = 0;
            for (Path p : xlsx) {
                try {
                    importer.importBytes(p.getFileName().toString(), Files.readAllBytes(p), true);
                    ok++;
                } catch (Exception e) {
                    log.warn("培养方案《{}》导入失败：{}", p.getFileName(), e.getMessage());
                }
            }
            log.info("培养方案自动导入完成，成功 {} 份，共发现 {} 个文件", ok, xlsx.size());
        } catch (IOException e) {
            log.warn("读取培养方案目录失败：{}", e.getMessage());
        }
    }

}
