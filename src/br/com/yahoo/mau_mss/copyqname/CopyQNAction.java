/*
 * Copyright 2014, 2015 Mauricio Soares da Silva.
 *
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Tradução não-oficial:
 *
 * Este programa é um software livre; você pode redistribuí-lo e/ou
 *   modificá-lo dentro dos termos da Licença Pública Geral GNU como
 *   publicada pela Fundação do Software Livre (FSF); na versão 3 da
 *   Licença, ou (na sua opinião) qualquer versão.
 *
 *   Este programa é distribuído na esperança de que possa ser útil,
 *   mas SEM NENHUMA GARANTIA; sem uma garantia implícita de ADEQUAÇÃO
 *   a qualquer MERCADO ou APLICAÇÃO EM PARTICULAR. Veja a
 *   Licença Pública Geral GNU para maiores detalhes.
 *
 *   Você deve ter recebido uma cópia da Licença Pública Geral GNU junto
 *   com este programa. Se não, veja <http://www.gnu.org/licenses/>.
 */
package br.com.yahoo.mau_mss.copyqname;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.api.java.source.CancellableTask;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.util.datatransfer.ExClipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 * Title: CopyQNAction
 * Description: Plugin para copiar o nome completamente qualificado de um elemento
 * Date: May 30, 2014, 8:07:00 PM
 *
 * @author Mauricio Soares da Silva (mauricio.soares)
 * @see https://platform.netbeans.org/tutorials/nbm-copyfqn.html
 */
@ActionID(category = "Edit",
        id = "br.com.yahoo.mau_mss.copyqname.CopyQNAction")
@ActionRegistration(iconBase = "resources/copy.png",
        displayName = "#CTL_CopyQNAction")
@ActionReferences({
  @ActionReference(path = "Menu/Tools", position = 190),
  @ActionReference(path = "Editors/text/x-java/Popup", position = 3030)
})
@Messages("CTL_CopyQNAction=Copy Qualified Name")
public class CopyQNAction implements ActionListener {
  private final DataObject dataObject;
  private Clipboard clipboard;
  private static final Logger logger = Logger.getLogger(CopyQNAction.class.getName());
  private static final long serialVersionUID = 1L;

  public CopyQNAction(DataObject context) {
    this.dataObject = context;
    this.clipboard = Lookup.getDefault().lookup(ExClipboard.class);
    if (this.clipboard == null) {
      this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }
  }

  @Override
  public void actionPerformed(ActionEvent ev) {
    if (this.dataObject == null) {
      return;
    }
    final FileObject fileObject = this.dataObject.getPrimaryFile();
    if (fileObject == null) {
      return;
    }
    JavaSource javaSource = JavaSource.forFileObject(fileObject);
    if (javaSource == null) {
      StatusDisplayer.getDefault().setStatusText("It isn't a Java file: " + fileObject.getPath());
      return;
    }
    try {
      javaSource.runUserActionTask(new CopyTask(), true);
    } catch (IOException e) {
      CopyQNAction.logger.log(Level.SEVERE, "Erro ao copiar elemento.", e);
      Exceptions.printStackTrace(e);
    }
  }

  private void setClipboardContents(CompilationController compilationController) {
    List<? extends Tree> typeDeclsTrees = compilationController.getCompilationUnit().getTypeDecls();
    ClassTree firstClassTree = null;
    for (Tree tree : typeDeclsTrees) {
      if (tree instanceof ClassTree) {
        ClassTree classTree = (ClassTree) tree;
        // Prefere classes públicas
        if (classTree.getModifiers().getFlags().contains(Modifier.PUBLIC)) {
          setClipboardContents(compilationController, classTree);
          return;
        }
        firstClassTree = (ClassTree) tree;
      }
    }
    if (firstClassTree != null) {
      setClipboardContents(compilationController, firstClassTree);
    }
  }

  private void setClipboardContents(CompilationController compilationController, ClassTree classTree) {
    Trees trees = compilationController.getTrees();
    TreePath treePath = trees.getPath(compilationController.getCompilationUnit(),
            classTree);
    if (treePath == null) {
      return;
    }
    TypeElement typeElement = (TypeElement) trees.getElement(treePath);
    if (typeElement != null) {
      setClipboardContents(typeElement.getQualifiedName().toString());
    }
  }

  private void setClipboardContents(String content) {
    if (this.clipboard == null) {
      return;
    }
    if (content == null) {
      StatusDisplayer.getDefault().setStatusText("");
      this.clipboard.setContents(null, null);
    } else {
      StatusDisplayer.getDefault().setStatusText(content);
      this.clipboard.setContents(new StringSelection(content), null);
    }
  }

  private class CopyTask implements CancellableTask<CompilationController> {

    @Override
    public void cancel() {
    }

    /**
     * Dispara o processo de cópia
     */
    @Override
    public void run(CompilationController compilationController) throws IOException {
      // Move para fase resolved
      compilationController.toPhase(Phase.ELEMENTS_RESOLVED);
      // Pega o documento se estiver aberto
      Document document = compilationController.getDocument();
      if (document != null) {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor.getDocument() == document) {
          // Pega a posição do caracter
          int dot = editor.getCaret().getDot();
          // Encontra o TreePath para posição do caracter
          TreePath tp = compilationController.getTreeUtilities().pathFor(dot);
          // Pega o Element
          Element element = compilationController.getTrees().getElement(tp);
          // ===== Elemento - classe de uma variável ou método =======
          if (element instanceof TypeElement) {
            setClipboardContents(((TypeElement) element).getQualifiedName().toString());
            return;
          }
          // ================== variável - atributo ==================
          if (element instanceof VariableElement) {
            VariableElement variableElement = (VariableElement) element;
            setClipboardContents(variableElement.asType().toString() + "." + variableElement.getSimpleName());
            return;
          }
          if (element instanceof ExecutableElement) {
            ExecutableElement executableElement = (ExecutableElement) element;
            // ======================= Método ========================
            if (element.getKind() == ElementKind.METHOD) {
              setClipboardContents(getFullQualifiedMethodNameAndParameters(executableElement, false));
              // ====================== Construtor =====================
            } else if (element.getKind() == ElementKind.CONSTRUCTOR) {
              setClipboardContents(getFullQualifiedMethodNameAndParameters(executableElement, true));
            }
            return;
          }
        }
      }
      // ======= Apenas use Classes (preferivelmente públicas) no arquivo
      setClipboardContents(compilationController);
    }

    /**
     * Simula o copy qualified name do Eclipse para métodos.
     * executableElement.toString() -> methodName(foo.Clazz)
     * executableElement.getReturnType().toString() -> void
     *
     * @param executableElement
     * @param constructor
     * @see
     * http://grepcode.com/file/repo1.maven.org$maven2@sk.seges.sesam$sesam-showcase-playground@1.1.3@sk$seges$corpis$core$pap$transfer$TransferObjectProcessor.java
     */
    private String getFullQualifiedMethodNameAndParameters(ExecutableElement executableElement, boolean constructor) {
      StringBuilder sb = new StringBuilder();
      sb.append(((TypeElement) (executableElement.getEnclosingElement())).getQualifiedName().toString());
      if (!constructor) {
        sb.append("#").append(executableElement.getSimpleName());
      }
      sb.append("(");
      List<? extends VariableElement> parameters = executableElement.getParameters();
      if (parameters != null) {
        for (int i = 0; i < parameters.size(); i++) {
          VariableElement ve = parameters.get(i);
          if (ve.asType() instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) ve.asType();
            sb.append(declaredType.asElement().getSimpleName());
          } else {
            sb.append(ve.asType().toString());
          }
          if (i + 1 < parameters.size()) {
            sb.append(", ");
          }
        }
      }
      sb.append(")");
      return sb.toString();
    }
  }

}
