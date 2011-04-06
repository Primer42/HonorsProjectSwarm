package simulation;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class SimulationController extends JFrame implements PropertyChangeListener, ActionListener, ItemListener {

	private static final long serialVersionUID = 2089545078800743566L;

	// the world we're simulating
	private World world;

	// initial values for fields
	private int numBots = 200;
	private int numSurvivors = 5;
	private double timeBetweenTimestepsInSeconds = 0;
	private static final boolean DRAW_BOT_RADII_INIT_VALUE = false;

	// buttons to control the simulation
	private JButton runSimulationButton;;
	private JButton stopSimulationButton;
	private JButton resetSimulationButton;

	//check boxes to set values
	private JCheckBox drawBotRadiiCheckBox;

	// feilds for variable entry
	private JFormattedTextField numBotsField;
	private JFormattedTextField numSurvivorsField;
	private JFormattedTextField timeBetweenTimestepsField; //TODO Make this into a slider

	// Formats to parse numbers in fields
	private NumberFormat numBotsFormat;
	private NumberFormat numSurvivorsFormat;
	private NumberFormat timeBetweenTimestepsFormat;

	// Label objects for fields and components
	private JLabel numBotsLabel;
	private JLabel numSurvivorsLabel;
	private JLabel timeBetweenTimestepsLabel;
	private JLabel drawBotRadiiLabel;

	// Label strings
	private final String runSimulationString = "Run";
	private final String stopSimulationString = "Stop";
	private final String resetSimulationString = "Reset";

	private final String numBotsString = "Number of Bots: ";
	private final String numSurvivorsString = "Number of Survivors: ";
	private final String timeBetweenTimestepsString = "Time between timesteps (seconds) :";
	private final String drawBotRadiiString = "Draw bot radii: ";	

	//TODO add a field that highlights a certain bot num

	public SimulationController() {
		super("Simulation Controller");

		// set up the window
		setResizable(false);

		setFocusable(true);

		setUpLables();

		// set everything up
		setUpFormats();
		setUpFields();
		setUpButtons();

		//lay everything out
		JPanel variablesPanel = layoutVariableComponentsAndLabels();
		JPanel buttonsPanel = layoutButtons();

		//put them into the overall layout
		add(variablesPanel, BorderLayout.NORTH);
		add(buttonsPanel, BorderLayout.SOUTH);
	}

	private void setUpLables() {
		numBotsLabel = new JLabel(numBotsString);
		numSurvivorsLabel = new JLabel(numSurvivorsString);
		timeBetweenTimestepsLabel = new JLabel(timeBetweenTimestepsString);
		drawBotRadiiLabel = new JLabel(drawBotRadiiString);
	}

	private void setUpFormats() {
		numBotsFormat = new DecimalFormat("###");
		numBotsFormat.setMaximumIntegerDigits(3);
		numBotsFormat.setMinimumIntegerDigits(1);

		numSurvivorsFormat = new DecimalFormat("###");
		numSurvivorsFormat.setMaximumIntegerDigits(3);
		numSurvivorsFormat.setMinimumIntegerDigits(1);

		timeBetweenTimestepsFormat = new DecimalFormat("##.#");
		timeBetweenTimestepsFormat.setMaximumIntegerDigits(2);
		timeBetweenTimestepsFormat.setMinimumIntegerDigits(0);
	}

	private void setUpFields() {
		int numFieldColumns = 3;

		numBotsField = new JFormattedTextField(numBotsFormat);
		numBotsField.setValue(new Integer(numBots));
		numBotsField.setColumns(numFieldColumns);
		numBotsField.addPropertyChangeListener("value", this);
		numBotsLabel.setLabelFor(numBotsField);

		numSurvivorsField = new JFormattedTextField(numSurvivorsFormat);
		numSurvivorsField.setValue(new Integer(numSurvivors));
		numSurvivorsField.setColumns(numFieldColumns);
		numSurvivorsField.addPropertyChangeListener("value", this);
		numSurvivorsLabel.setLabelFor(numSurvivorsField);

		timeBetweenTimestepsField = new JFormattedTextField(timeBetweenTimestepsFormat);
		timeBetweenTimestepsField.setValue(new Double(timeBetweenTimestepsInSeconds));
		timeBetweenTimestepsField.setColumns(numFieldColumns);
		timeBetweenTimestepsField.addPropertyChangeListener("value", this);
		timeBetweenTimestepsLabel.setLabelFor(timeBetweenTimestepsField);
	}	

	private void setUpButtons() {
		runSimulationButton = new JButton(runSimulationString);
		runSimulationButton.addActionListener(this);

		stopSimulationButton = new JButton(stopSimulationString);
		stopSimulationButton.addActionListener(this);

		resetSimulationButton = new JButton(resetSimulationString);
		resetSimulationButton.addActionListener(this);

		drawBotRadiiCheckBox = new JCheckBox();
		drawBotRadiiCheckBox.setSelected(DRAW_BOT_RADII_INIT_VALUE);
		drawBotRadiiCheckBox.addItemListener(this);
	}


	private JPanel layoutVariableComponentsAndLabels() {
		JPanel panel = new JPanel(new GridLayout(0,2));
		panel.add(numBotsLabel);
		panel.add(numBotsField);

		panel.add(numSurvivorsLabel);
		panel.add(numSurvivorsField);

		panel.add(timeBetweenTimestepsLabel);
		panel.add(timeBetweenTimestepsField);

		panel.add(drawBotRadiiLabel);
		panel.add(drawBotRadiiCheckBox);

		return panel;
	}

	private JPanel layoutButtons() {
		JPanel panel = new JPanel(new GridLayout(1, 0));
		panel.add(resetSimulationButton);
		panel.add(stopSimulationButton);
		panel.add(runSimulationButton);

		return panel;
	}


	@Override
	public void propertyChange(PropertyChangeEvent e) {
		Object source = e.getSource();

		if(source == numBotsField) {
			numBots = ((Number)numBotsField.getValue()).intValue();
			if(numBots < 0) {
				numBots *= -1;
				numBotsField.setValue(new Integer(numBots));
			}
		}

		if(source == numSurvivorsField) {
			numSurvivors = ((Number)numSurvivorsField.getValue()).intValue();
			if(numSurvivors < 0) {
				numSurvivors *= -1;
				numSurvivorsField.setValue(new Integer(numSurvivors));
			}
		}

		if(source == timeBetweenTimestepsField) {
			timeBetweenTimestepsInSeconds = ((Number)timeBetweenTimestepsField.getValue()).doubleValue();
			if(timeBetweenTimestepsInSeconds < 0) {
				timeBetweenTimestepsInSeconds *= -1;
				timeBetweenTimestepsField.setValue(new Double(timeBetweenTimestepsInSeconds));
			}

			if(world != null) {
				world.setTimeBetweenTimesteps((long)(timeBetweenTimestepsInSeconds * 1000)); //convert from seconds to milliseconds
			}
		}
	}

	private void makeTheWorld() {
		if(world != null) {
			world.stopSimulation();
			world.dispose();
		}

		//choose the zone directory location, or press cancel if want to create randomly
		JFileChooser zoneDirChooser = new JFileChooser();
		zoneDirChooser.setCurrentDirectory(new File("."));
		zoneDirChooser.setDialogTitle("Choose a zone directory, or cancel for random creation");
		zoneDirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		zoneDirChooser.setAcceptAllFileFilterUsed(false);

		if(zoneDirChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			world = new World(numBots, numSurvivors, (long)(timeBetweenTimestepsInSeconds * 1000), drawBotRadiiCheckBox.isSelected(), zoneDirChooser.getSelectedFile());
		} else {
			world = new World(numBots, numSurvivors, (long)(timeBetweenTimestepsInSeconds*1000), drawBotRadiiCheckBox.isSelected());
		}
		world.setDrawBotRadii(drawBotRadiiCheckBox.isSelected());
		//		world.pack();
		world.setLocation(this.getX(), this.getY() + this.getHeight());
		world.setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();

		if(source == runSimulationButton) {
			SwingUtilities.invokeLater(new Runnable() {

				@Override
				public void run() {
					if(world == null) {
						makeTheWorld();
					}

					if(! world.isGoing()) {
						//put the running in a seperate thread
						new Thread(new Runnable() {

							@Override
							public void run() {
								world.go();	
							}
						}).start();
					}
				}
			});
		}

		if(source == stopSimulationButton) {
			if(world != null) {
				world.stopSimulation();
			}
		}

		if(source == resetSimulationButton) {
			makeTheWorld();
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		Object source = e.getItemSelectable();

		if(world!= null && source == drawBotRadiiCheckBox) {
			world.setDrawBotRadii(e.getStateChange() == ItemEvent.SELECTED);
		}
	}

	public static void createAndShowGUI() {
		SimulationController simulationController = new SimulationController();
		simulationController.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		simulationController.pack();

		simulationController.runSimulationButton.requestFocusInWindow();
		simulationController.setLocation(800, 20);

		simulationController.setVisible(true);
	}

	public static void main(String[] args) {
		System.out.println("******\nPlease make sure I have lots of memory by adding the flag '-Xmx10G' to the java command! Thanks!\n*****");
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				createAndShowGUI();
			}
		});
	}

}
