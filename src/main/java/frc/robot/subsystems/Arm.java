// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.TalonFX;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.ArmFeedforward;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.trajectory.TrapezoidProfile.Constraints;
import edu.wpi.first.wpilibj.AnalogPotentiometer;
import edu.wpi.first.wpilibj.DutyCycleEncoder;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.RobotContainer;
import frc.robot.constants.ArmConstants;
import frc.robot.constants.CANConstants;
import frc.robot.constants.ArmConstants.*;
import frc.robot.trobot5013lib.RevThroughBoreEncoder;

import static frc.robot.constants.ArmConstants.*;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.math.util.Units;



/** Add your docs here. */
public class Arm extends SubsystemBase {
    private final TalonFX m_extensionMotor = new TalonFX(CANConstants.EXTENSION_ID);
    private final TalonFX m_rotationMotor = new TalonFX(CANConstants.SHOULDER_ID);
    private final PIDController m_extensionPIDController = new PIDController(ExtensionGains.kP, ExtensionGains.kI, ExtensionGains.kD);
    private final Constraints m_rotationConstraints = new Constraints(RotationConstraints.MAX_ROTATION_VELOCITY_RPS, RotationConstraints.MAX_ROTATION_ACCELERATION_RPSPS);
    private final PIDController m_rotationPIDController = new PIDController(RotationGains.kP, RotationGains.kI, RotationGains.kD);
    private final AnalogPotentiometer m_potentiometer = new AnalogPotentiometer(CANConstants.ARM_ANGLE_ENCODER);
    private final RevThroughBoreEncoder m_angleEncoder = new RevThroughBoreEncoder(CANConstants.ARM_ANGLE_ENCODER);
    private ArmFeedforward m_rotationFeedForward = new ArmFeedforward(RotationGains.kS, RotationGains.kG, RotationGains.kV); 
    private double angleSetpointRadians ;
    private boolean isOpenLoopRotation = true;
    private boolean isOpenLooExtension = true;
    private double extensionSetpoint; 


    public Arm() {
        m_rotationPIDController.enableContinuousInput(0, 2 * Math.PI);
        m_extensionMotor.configFactoryDefault();
        m_extensionMotor.setNeutralMode(NeutralMode.Brake);
        m_angleEncoder.setInverted(true);
        m_angleEncoder.setOffset(ArmConstants.ARM_OFFSET_DEGREES);
        setAngleSetpointRadians(getArmAngleRadians());
        m_rotationPIDController.setTolerance(RotationGains.TOLERANCE.getRadians());
        isOpenLoopRotation = false;
        SmartDashboard.putData("Arm Rotation PID Controller", m_rotationPIDController);
    }

    
    public double  getExtensionSetpoint() {
        return extensionSetpoint;
    }

    public void setExtensionSetpoint(double extensionSetpoint) {
        this.extensionSetpoint = extensionSetpoint;
    }
	public PIDController getExtensionPIDController() {
		return m_extensionPIDController;
	}

    public PIDController getRotationPIDController() {
        return m_rotationPIDController;
    }
    public Command rotateToCommand(Rotation2d angle) {
        return run(() -> setAngleSetpointRadians(angle.getRadians()))
            .until(m_extensionPIDController::atSetpoint)
            .andThen(runOnce(() -> hold()));
    }

    public void rotate(double percent) {
        if (percent == 0.0){
            if (isOpenLoopRotation){
                hold();
            }
        } else {
            isOpenLoopRotation = true;
            m_rotationMotor.set(ControlMode.PercentOutput, percent);
        }
    }

    public Command extendToCommand(double length) {
        m_extensionPIDController.setTolerance(length);
        return run(() -> setExtensionSetpoint(length))
        .until(m_extensionPIDController::atSetpoint)
        .andThen(runOnce(() -> holdExtension()));
    }

    public Command extendAndRotateCommand(Rotation2d angle, double length) {
        m_extensionPIDController.setTolerance(length);
        return run(() -> setExtensionAndRotation(angle.getRadians(),length))
        .until(this::isExtenstionAndRotationAtSetpoint)
        .andThen(runOnce(() -> holdExtension()));
    }

    public void setExtensionAndRotation(double angle, double length){
       setExtensionSetpoint(length);
       setAngleSetpointRadians(angle);
    }

    public boolean isExtenstionAndRotationAtSetpoint(){
        return m_extensionPIDController.atSetpoint() && m_rotationPIDController.atSetpoint();
    }

    public void extend(double percent) {
        if (percent == 0.0){
            if (isOpenLooExtension){
                holdExtension();
            }
        } else {
            isOpenLooExtension = true;
            m_extensionMotor.set(ControlMode.PercentOutput, percent);
        }
       
    }

    public void extendClosedLoop(double velocity) {
        isOpenLooExtension = false;
        double feedForward = 0; //calculate feed forward
        m_extensionMotor.set(ControlMode.PercentOutput, RobotContainer.voltageToPercentOutput(feedForward));
    }

    public void rotateClosedLoop(double velocity) {
        isOpenLoopRotation = false;
        SmartDashboard.putNumber("OUTPUT", velocity);
        double feedForward = m_rotationFeedForward.calculate(getArmAngleRadians(),velocity);
        SmartDashboard.putNumber("FeedForward", feedForward);
        SmartDashboard.putNumber("Voltage",RobotContainer.voltageToPercentOutput(feedForward));
        m_rotationMotor.set(ControlMode.PercentOutput, RobotContainer.voltageToPercentOutput(feedForward));
    }

    public double getArmAngleRadians(){
        return ((m_angleEncoder.getAngle()).getRadians());
    }

    public double getAngleSetpointRadians() {
        return angleSetpointRadians;
    }

    public void setAngleSetpointRadians(double angleSetpoint) {
        this.angleSetpointRadians = angleSetpoint;
    }

    public void hold(){
        setAngleSetpointRadians(getArmAngleRadians());
        isOpenLoopRotation = false;
    }

    public void holdExtension(){
        setExtensionSetpoint(getCurrentExtension());
        isOpenLooExtension = false;
    }

    @Override
    public void periodic(){
        SmartDashboard.putNumber("Arm Angle", (m_angleEncoder.getAngle()).getDegrees());
        if (isOpenLoopRotation) {
            m_rotationPIDController.reset();
        } else {
            SmartDashboard.putNumber("Setpoint", getAngleSetpointRadians());
            SmartDashboard.putNumber("Measurement", getArmAngleRadians());
            SmartDashboard.putNumber("Error", getAngleSetpointRadians() - getArmAngleRadians() );
            m_rotationPIDController.setSetpoint(getAngleSetpointRadians());
            rotateClosedLoop(m_rotationPIDController.calculate(getArmAngleRadians()));
        }

        if (isOpenLooExtension) {
            m_extensionPIDController.reset();
        } else {
            m_extensionPIDController.setSetpoint(getExtensionSetpoint());
            extendClosedLoop(m_extensionPIDController.calculate(getCurrentExtension()));
        }
    }

    public double getCurrentExtension(){
        return m_potentiometer == null?0:m_potentiometer.get();
    }

}

