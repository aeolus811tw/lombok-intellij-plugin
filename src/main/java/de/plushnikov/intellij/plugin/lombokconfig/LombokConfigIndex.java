package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import de.plushnikov.intellij.plugin.language.LombokConfigFileType;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigCleaner;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigFile;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigProperty;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigPsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LombokConfigIndex extends FileBasedIndexExtension<ConfigIndexKey, String> {
  @NonNls
  public static final ID<ConfigIndexKey, String> NAME = ID.create("LombokConfigIndex");

  private static final int INDEX_FORMAT_VERSION = 5;

  @NotNull
  @Override
  public ID<ConfigIndexKey, String> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public DataIndexer<ConfigIndexKey, String, FileContent> getIndexer() {
    return new DataIndexer<ConfigIndexKey, String, FileContent>() {
      @NotNull
      @Override
      public Map<ConfigIndexKey, String> map(@NotNull FileContent inputData) {
        Map<ConfigIndexKey, String> result = Collections.emptyMap();

        final VirtualFile directoryFile = inputData.getFile().getParent();
        if (null != directoryFile) {
          final String canonicalPath = PathUtil.toSystemIndependentName(directoryFile.getCanonicalPath());
          if (null != canonicalPath) {
            final Map<String, String> configValues = extractValues((LombokConfigFile) inputData.getPsiFile());

            result = new HashMap<ConfigIndexKey, String>();
            for (Map.Entry<String, String> entry : configValues.entrySet()) {
              result.put(new ConfigIndexKey(canonicalPath, entry.getKey()), entry.getValue());
            }
          }
        }

        return result;
      }

      private Map<String, String> extractValues(LombokConfigFile configFile) {
        Map<String, String> result = new HashMap<String, String>();

        final LombokConfigCleaner[] configCleaners = LombokConfigUtil.getLombokConfigCleaners(configFile);
        for (LombokConfigCleaner configCleaner : configCleaners) {
          final String key = LombokConfigPsiUtil.getKey(configCleaner);

          final ConfigKey configKey = ConfigKey.fromConfigStringKey(key);
          if (null != configKey) {
            result.put(key, configKey.getConfigDefaultValue());
          }
        }

        final LombokConfigProperty[] configProperties = LombokConfigUtil.getLombokConfigProperties(configFile);
        for (LombokConfigProperty configProperty : configProperties) {
          final String key = LombokConfigPsiUtil.getKey(configProperty);
          final String value = LombokConfigPsiUtil.getValue(configProperty);
          final String sign = LombokConfigPsiUtil.getSign(configProperty);
          if (null == sign) {
            result.put(key, value);
          } else {
            final String resultValue = result.get(key);
            final String previousValue = null == resultValue ? "" : resultValue;
            final String combinedValue = previousValue + sign + value + ";";
            result.put(key, combinedValue);
          }
        }

        return result;
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<ConfigIndexKey> getKeyDescriptor() {
    return new KeyDescriptor<ConfigIndexKey>() {
      @Override
      public int getHashCode(ConfigIndexKey configKey) {
        return configKey.hashCode();
      }

      @Override
      public boolean isEqual(ConfigIndexKey val1, ConfigIndexKey val2) {
        return val1.equals(val2);
      }

      @Override
      public void save(@NotNull DataOutput out, ConfigIndexKey value) throws IOException {
        out.writeUTF(StringUtil.notNullize(value.getDirectoryName()));
        out.writeUTF(StringUtil.notNullize(value.getConfigKey()));
      }

      @Override
      public ConfigIndexKey read(@NotNull DataInput in) throws IOException {
        return new ConfigIndexKey(in.readUTF(), in.readUTF());
      }
    };
  }

  @NotNull
  @Override
  public DataExternalizer<String> getValueExternalizer() {
    return new EnumeratorStringDescriptor();
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new FileBasedIndex.InputFilter() {
      @Override
      public boolean acceptInput(VirtualFile file) {
        return LombokConfigFileType.INSTANCE.equals(file.getFileType());
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return INDEX_FORMAT_VERSION;
  }
}
